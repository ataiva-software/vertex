package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/spf13/cobra"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gopkg.in/yaml.v3"
)

func TestFormatOutput(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		format   string
		expected string
		wantErr  bool
	}{
		{
			name:     "JSON format",
			input:    `{"key":"value"}`,
			format:   "json",
			expected: `{"key":"value"}`,
			wantErr:  false,
		},
		{
			name:     "YAML format",
			input:    `{"key":"value"}`,
			format:   "yaml",
			expected: "key: value\n",
			wantErr:  false,
		},
		{
			name:     "Invalid JSON",
			input:    `{invalid}`,
			format:   "yaml",
			expected: "",
			wantErr:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := formatOutput(tt.input, tt.format)
			if tt.wantErr {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, result)
			}
		})
	}
}

func TestMakeRequest(t *testing.T) {
	// Create test server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/success":
			w.WriteHeader(http.StatusOK)
			w.Write([]byte(`{"status":"success"}`))
		case "/error":
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte(`{"error":"server error"}`))
		case "/post":
			if r.Method == "POST" {
				w.WriteHeader(http.StatusCreated)
				w.Write([]byte(`{"created":true}`))
			}
		}
	}))
	defer server.Close()

	tests := []struct {
		name     string
		method   string
		url      string
		body     interface{}
		wantErr  bool
		contains string
	}{
		{
			name:     "GET success",
			method:   "GET",
			url:      server.URL + "/success",
			body:     nil,
			wantErr:  false,
			contains: "success",
		},
		{
			name:     "GET error",
			method:   "GET",
			url:      server.URL + "/error",
			body:     nil,
			wantErr:  true,
			contains: "HTTP 500",
		},
		{
			name:     "POST with body",
			method:   "POST",
			url:      server.URL + "/post",
			body:     map[string]string{"key": "value"},
			wantErr:  false,
			contains: "created",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := makeRequest(tt.method, tt.url, tt.body)
			if tt.wantErr {
				assert.Error(t, err)
				assert.Contains(t, err.Error(), tt.contains)
			} else {
				assert.NoError(t, err)
				assert.Contains(t, result, tt.contains)
			}
		})
	}
}

func TestVaultCommands(t *testing.T) {
	// Test vault command structure
	cmd := vaultCmd()
	assert.Equal(t, "vault", cmd.Use)
	assert.Equal(t, "Manage secrets", cmd.Short)
	
	// Check subcommands
	subcommands := cmd.Commands()
	assert.Len(t, subcommands, 5) // list, get, store, update, delete
	
	commandNames := make([]string, len(subcommands))
	for i, subcmd := range subcommands {
		commandNames[i] = subcmd.Use
	}
	assert.Contains(t, commandNames, "list")
	assert.Contains(t, commandNames, "get [key]")
	assert.Contains(t, commandNames, "store [key] [value]")
	assert.Contains(t, commandNames, "update [key] [value]")
	assert.Contains(t, commandNames, "delete [key]")
}

func TestAllCommandsHaveFormatFlag(t *testing.T) {
	commands := []struct {
		name string
		cmd  *cobra.Command
	}{
		{"vault", vaultCmd()},
		{"flow", flowCmd()},
		{"task", taskCmd()},
		{"monitor", monitorCmd()},
		{"sync", syncCmd()},
		{"insight", insightCmd()},
		{"hub", hubCmd()},
	}

	for _, tc := range commands {
		t.Run(tc.name, func(t *testing.T) {
			// Check that each command has subcommands with format flags
			subcommands := tc.cmd.Commands()
			assert.Greater(t, len(subcommands), 0, "Command should have subcommands")
			
			for _, subcmd := range subcommands {
				formatFlag := subcmd.Flags().Lookup("format")
				assert.NotNil(t, formatFlag, "Subcommand %s should have --format flag", subcmd.Use)
				assert.Equal(t, "json", formatFlag.DefValue, "Default format should be json")
			}
		})
	}
}

func TestCLIIntegration(t *testing.T) {
	// Test that commands can be executed without panicking
	commands := []string{
		"vault --help",
		"flow --help", 
		"task --help",
		"monitor --help",
		"sync --help",
		"insight --help",
		"hub --help",
	}

	for _, cmdStr := range commands {
		t.Run(cmdStr, func(t *testing.T) {
			// Capture output
			old := os.Stdout
			r, w, _ := os.Pipe()
			os.Stdout = w

			// Execute command
			parts := strings.Fields(cmdStr)
			rootCmd := &cobra.Command{Use: "vertex"}
			rootCmd.AddCommand(vaultCmd(), flowCmd(), taskCmd(), monitorCmd(), syncCmd(), insightCmd(), hubCmd())
			rootCmd.SetArgs(parts)
			
			err := rootCmd.Execute()
			
			// Restore stdout
			w.Close()
			os.Stdout = old
			
			// Read output
			buf := make([]byte, 1024)
			n, _ := r.Read(buf)
			output := string(buf[:n])
			
			assert.NoError(t, err)
			assert.Contains(t, output, "Usage:")
		})
	}
}

func TestJSONYAMLConsistency(t *testing.T) {
	testData := map[string]interface{}{
		"secrets": []map[string]interface{}{
			{
				"key":         "test-key",
				"description": "test description",
				"created_at":  "2025-01-01T00:00:00Z",
			},
		},
	}

	jsonBytes, err := json.Marshal(testData)
	require.NoError(t, err)
	jsonStr := string(jsonBytes)

	// Test JSON output
	jsonOutput, err := formatOutput(jsonStr, "json")
	require.NoError(t, err)
	assert.Equal(t, jsonStr, jsonOutput)

	// Test YAML output
	yamlOutput, err := formatOutput(jsonStr, "yaml")
	require.NoError(t, err)
	
	// Parse YAML back to verify structure
	var yamlData interface{}
	err = yaml.Unmarshal([]byte(yamlOutput), &yamlData)
	require.NoError(t, err)
	
	// Convert back to JSON for comparison
	yamlAsJSON, err := json.Marshal(yamlData)
	require.NoError(t, err)
	
	assert.JSONEq(t, jsonStr, string(yamlAsJSON))
}

func TestErrorHandling(t *testing.T) {
	// Test invalid URL
	_, err := makeRequest("GET", "invalid-url", nil)
	assert.Error(t, err)
	
	// Test invalid format
	_, err = formatOutput(`{"key":"value"}`, "invalid")
	assert.NoError(t, err) // Should default to JSON
}

// Benchmark tests
func BenchmarkFormatOutputJSON(b *testing.B) {
	input := `{"key":"value","number":123,"array":[1,2,3]}`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		formatOutput(input, "json")
	}
}

func BenchmarkFormatOutputYAML(b *testing.B) {
	input := `{"key":"value","number":123,"array":[1,2,3]}`
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		formatOutput(input, "yaml")
	}
}

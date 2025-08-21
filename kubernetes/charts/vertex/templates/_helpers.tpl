{{/*
Create a default fully qualified app name.
*/}}
{{- define "eden.fullname" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "eden.labels" -}}
app.kubernetes.io/part-of: eden-devops-suite
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Service template
*/}}
{{- define "eden.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ .serviceName }}
  namespace: {{ .root.Release.Namespace }}
  labels:
    app: {{ .serviceName }}
    tier: backend
    {{- include "eden.labels" .root | nindent 4 }}
spec:
  type: {{ .serviceConfig.service.type }}
  ports:
  - port: {{ .serviceConfig.service.port }}
    targetPort: 8080
    name: http
  selector:
    app: {{ .serviceName }}
{{- end -}}

{{/*
Deployment template
*/}}
{{- define "eden.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .serviceName }}
  namespace: {{ .root.Release.Namespace }}
  labels:
    app: {{ .serviceName }}
    {{- include "eden.labels" .root | nindent 4 }}
spec:
  replicas: {{ .serviceConfig.replicaCount }}
  selector:
    matchLabels:
      app: {{ .serviceName }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: {{ .serviceName }}
        tier: backend
        {{- include "eden.labels" .root | nindent 8 }}
    spec:
      containers:
      - name: {{ .serviceName }}
        image: "{{ .root.Values.global.image.repository }}:{{ .root.Values.global.image.tag }}"
        imagePullPolicy: {{ .root.Values.global.image.pullPolicy }}
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: {{ .root.Values.global.environment | quote }}
        - name: DATABASE_URL
          value: "postgresql://$(POSTGRES_USER):$(POSTGRES_PASSWORD)@{{ .root.Values.global.database.host }}:{{ .root.Values.global.database.port }}/{{ .dbName }}"
        - name: REDIS_URL
          value: "redis://{{ .root.Values.global.redis.host }}:{{ .root.Values.global.redis.port }}"
        - name: LOG_LEVEL
          valueFrom:
            configMapKeyRef:
              name: eden-config
              key: LOG_LEVEL
        - name: API_GATEWAY_URL
          valueFrom:
            configMapKeyRef:
              name: eden-config
              key: API_GATEWAY_URL
        {{- if .extraEnv }}
        {{- range .extraEnv }}
        - name: {{ .name }}
          valueFrom:
            configMapKeyRef:
              name: eden-config
              key: {{ .key }}
        {{- end }}
        {{- end }}
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: eden-secrets
              key: POSTGRES_USER
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: eden-secrets
              key: POSTGRES_PASSWORD
        resources:
          {{- toYaml .root.Values.global.resources | nindent 10 }}
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
{{- end -}}

{{/*
HorizontalPodAutoscaler template
*/}}
{{- define "eden.hpa" -}}
{{- if .serviceConfig.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ .serviceName }}-hpa
  namespace: {{ .root.Release.Namespace }}
  labels:
    {{- include "eden.labels" .root | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ .serviceName }}
  minReplicas: {{ .serviceConfig.autoscaling.minReplicas }}
  maxReplicas: {{ .serviceConfig.autoscaling.maxReplicas }}
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: {{ .serviceConfig.autoscaling.targetCPUUtilizationPercentage }}
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: {{ .serviceConfig.autoscaling.targetMemoryUtilizationPercentage }}
{{- end }}
{{- end -}}

{{/*
NetworkPolicy template
*/}}
{{- define "eden.networkpolicy" -}}
{{- if .root.Values.global.networkPolicy.enabled }}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ .serviceName }}-network-policy
  namespace: {{ .root.Release.Namespace }}
  labels:
    {{- include "eden.labels" .root | nindent 4 }}
spec:
  podSelector:
    matchLabels:
      app: {{ .serviceName }}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    - podSelector:
        matchLabels:
          tier: backend
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgresql
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
  {{- if .extraEgress }}
  {{- range .extraEgress }}
  - to:
    - podSelector:
        matchLabels:
          app: {{ .app }}
    ports:
    - protocol: TCP
      port: {{ .port }}
  {{- end }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
PodDisruptionBudget template
*/}}
{{- define "eden.pdb" -}}
{{- if .root.Values.global.pdb.enabled }}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ .serviceName }}-pdb
  namespace: {{ .root.Release.Namespace }}
  labels:
    {{- include "eden.labels" .root | nindent 4 }}
spec:
  minAvailable: {{ .root.Values.global.pdb.minAvailable }}
  selector:
    matchLabels:
      app: {{ .serviceName }}
{{- end }}
{{- end -}}
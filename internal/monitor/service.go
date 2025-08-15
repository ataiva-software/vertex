package monitor

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"gorm.io/gorm"
)

type Service struct {
	db *gorm.DB
}

func NewService() *Service {
	return &Service{}
}

func (s *Service) SetDB(db *gorm.DB) {
	s.db = db
}

func (s *Service) CreateMetric(ctx context.Context, metric *Metric) error {
	if err := s.validateMetric(metric); err != nil {
		return err
	}

	if err := s.db.Create(metric).Error; err != nil {
		return fmt.Errorf("failed to create metric: %w", err)
	}

	return nil
}

func (s *Service) GetMetrics(ctx context.Context, serviceName string) ([]*Metric, error) {
	var metrics []*Metric
	err := s.db.Where("service_name = ?", serviceName).Find(&metrics).Error
	if err != nil {
		return nil, fmt.Errorf("failed to get metrics: %w", err)
	}

	return metrics, nil
}

func (s *Service) CreateAlert(ctx context.Context, alert *Alert) error {
	if err := s.validateAlert(alert); err != nil {
		return err
	}

	if alert.Status == 0 {
		alert.Status = AlertStatusActive
	}

	if err := s.db.Create(alert).Error; err != nil {
		return fmt.Errorf("failed to create alert: %w", err)
	}

	return nil
}

func (s *Service) GetAlerts(ctx context.Context, userID string) ([]*Alert, error) {
	var alerts []*Alert
	err := s.db.Where("user_id = ?", userID).Find(&alerts).Error
	if err != nil {
		return nil, fmt.Errorf("failed to get alerts: %w", err)
	}

	return alerts, nil
}

func (s *Service) validateMetric(metric *Metric) error {
	if strings.TrimSpace(metric.ServiceName) == "" {
		return errors.New("service name is required")
	}
	if strings.TrimSpace(metric.Name) == "" {
		return errors.New("metric name is required")
	}
	return nil
}

func (s *Service) validateAlert(alert *Alert) error {
	if strings.TrimSpace(alert.Name) == "" {
		return errors.New("alert name is required")
	}
	if strings.TrimSpace(alert.UserID) == "" {
		return errors.New("user ID is required")
	}
	return nil
}

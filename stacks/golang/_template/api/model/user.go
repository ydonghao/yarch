// Package model API 层 DTO：协议形状（camelCase / ISO-8601 / ID 不透明 string，契约数据表示）。
package model

import "time"

// CreateUserRequest 创建用户请求。
type CreateUserRequest struct {
	Name string `json:"name"`
}

// UserResponse 用户响应（时间 ISO-8601 UTC）。
type UserResponse struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	CreatedAt time.Time `json:"createdAt"`
}

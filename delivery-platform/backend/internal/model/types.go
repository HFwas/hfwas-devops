package model

import "time"

// Cluster 集群注册
type Cluster struct {
	ID            int64  `json:"id"`
	Name          string `json:"name"`
	ServerHost    string `json:"serverHost"`
	KubeconfigEnc string `json:"-"` // 永不返回给前端
	IsCurrent     bool   `json:"isCurrent"`
	Status        string `json:"status"` // UNKNOWN / UP / DOWN
	Version       string `json:"version,omitempty"`
	Deleted       bool   `json:"-"`
	CreateBy      int64  `json:"-"`
	UpdateBy      int64  `json:"-"`
	CreateTime    string `json:"createTime"`
	UpdateTime    string `json:"updateTime"`
}

// Product 导入的产品包
type Product struct {
	ID               int64  `json:"id"`
	ProductKey       string `json:"productKey"`
	DisplayName      string `json:"displayName"`
	PackageVersion   string `json:"packageVersion"`
	PackageDir       string `json:"-"`
	ManifestJSON     string `json:"-"`
	CloudServiceName string `json:"cloudServiceName,omitempty"`
	CloudServiceNS   string `json:"cloudServiceNS,omitempty"`
	ParamsJSON       string `json:"-"`
	GlobalParamsJSON string `json:"-"`
	Status           string `json:"status"` // NOT_DEPLOYED / DEPLOYING / READY / DEGRADED / FAILED / UNINSTALLING
	ClusterID        *int64 `json:"clusterId,omitempty"`
	TargetNS         string `json:"targetNs,omitempty"`
	Deleted          bool   `json:"-"`
	CreateBy         int64  `json:"-"`
	UpdateBy         int64  `json:"-"`
	CreateTime       string `json:"createTime"`
	UpdateTime       string `json:"updateTime"`
}

// Deployment 部署记录
type Deployment struct {
	ID                 int64  `json:"id"`
	ProductID          int64  `json:"productId"`
	ClusterID          int64  `json:"clusterId"`
	Action             string `json:"action"` // DEPLOY / UPGRADE / ROLLBACK / UNINSTALL
	PackageVersion     string `json:"packageVersion"`
	ParamsSnapshotJSON string `json:"-"`
	GlobalSnapshotJSON string `json:"-"`
	AppName            string `json:"appName,omitempty"`
	AppNS              string `json:"appNS,omitempty"`
	Status             string `json:"status"` // PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED
	ErrorMessage       string `json:"errorMessage,omitempty"`
	StartedAt          string `json:"startedAt,omitempty"`
	FinishedAt         string `json:"finishedAt,omitempty"`
	CreateBy           int64  `json:"-"`
	CreateTime         string `json:"createTime"`
}

// ProductListResult 产品列表返回
type ProductListResult struct {
	ID                int64              `json:"id"`
	ProductKey        string             `json:"productKey"`
	DisplayName       string             `json:"displayName"`
	PackageVersion    string             `json:"packageVersion"`
	Status            string             `json:"status"`
	ClusterName       string             `json:"clusterName,omitempty"`
	TargetNS          string             `json:"targetNs,omitempty"`
	Phase             string             `json:"phase,omitempty"`
	AggregatedSummary *AggregatedSummary `json:"aggregatedSummary,omitempty"`
	LastDeployTime    string             `json:"lastDeployTime,omitempty"`
}

// AggregatedSummary App CR 聚合摘要
type AggregatedSummary struct {
	TotalServices    int `json:"totalServices"`
	ReadyServices    int `json:"readyServices"`
	DegradedServices int `json:"degradedServices"`
	FailedServices   int `json:"failedServices"`
	TotalComponents  int `json:"totalComponents"`
	ReadyComponents  int `json:"readyComponents"`
}

// FormSchema 参数表单 schema
type FormSchema struct {
	Groups []FormGroup `json:"groups"`
}

type FormGroup struct {
	ID     string      `json:"id"`
	Title  string      `json:"title"`
	Fields []FormField `json:"fields"`
}

type FormField struct {
	Path        string           `json:"path"`
	Label       string           `json:"label"`
	Type        string           `json:"type"`
	Default     interface{}      `json:"default,omitempty"`
	Required    bool             `json:"required,omitempty"`
	Placeholder string           `json:"placeholder,omitempty"`
	Validation  *FieldValidation `json:"validation,omitempty"`
	Value       interface{}      `json:"value,omitempty"`
	Options     []SelectOption   `json:"options,omitempty"`
}

type FieldValidation struct {
	Pattern   string   `json:"pattern,omitempty"`
	MinLength *int     `json:"minLength,omitempty"`
	MaxLength *int     `json:"maxLength,omitempty"`
	Minimum   *float64 `json:"minimum,omitempty"`
	Maximum   *float64 `json:"maximum,omitempty"`
	Enum      []string `json:"enum,omitempty"`
}

type SelectOption struct {
	Label string `json:"label"`
	Value string `json:"value"`
}

// ProductDetail 产品详情响应
type ProductDetail struct {
	Product
	Form  *FormSchema `json:"form"`
	Phase string      `json:"phase"`
	// 发布实例层（v0.3）：这一次发布的关联号与现场规划修订
	ReleaseID         string             `json:"releaseID,omitempty"`
	PlanRevision      string             `json:"planRevision,omitempty"`
	ObservedReleaseID string             `json:"observedReleaseID,omitempty"`
	AggregatedSummary *AggregatedSummary `json:"aggregatedSummary,omitempty"`
	ServiceStatuses   []ServiceStatus    `json:"serviceStatuses,omitempty"`
	// 发布步骤（ProductTask），用于展示「init job 先跑完，网关再起」这类顺序
	Tasks []ProductTaskView `json:"tasks,omitempty"`
}

// ProductTaskView 是发布步骤在白屏上的展示视图。
type ProductTaskView struct {
	Name      string   `json:"name"`
	ReleaseID string   `json:"releaseID,omitempty"`
	OpsType   string   `json:"opsType,omitempty"`
	Phase     string   `json:"phase,omitempty"`
	Component string   `json:"component,omitempty"`
	Resource  string   `json:"resource,omitempty"`
	After     []string `json:"after,omitempty"`
	Messages  []string `json:"messages,omitempty"`
}

type ServiceStatus struct {
	Name              string `json:"name"`
	Phase             string `json:"phase"`
	ReleaseID         string `json:"releaseID,omitempty"`
	ObservedReleaseID string `json:"observedReleaseID,omitempty"`
	Error             string `json:"error,omitempty"`
}

// 请求体
type ImportClusterRequest struct {
	Name           string `json:"name"`
	KubeconfigText string `json:"kubeconfigText"`
}

type SaveProductParamsRequest struct {
	DisplayName  string                 `json:"displayName,omitempty"`
	GlobalParams map[string]interface{} `json:"globalParams,omitempty"`
	Params       map[string]interface{} `json:"params,omitempty"`
	Overrides    map[string]interface{} `json:"overrides,omitempty"`
}

// Manifest 包 manifest 结构
type Manifest struct {
	APIVersion  string              `json:"apiVersion"`
	Key         string              `json:"key"`
	Version     string              `json:"version"`
	DisplayName string              `json:"displayName"`
	Description string              `json:"description,omitempty"`
	Icon        string              `json:"icon,omitempty"`
	Components  []ManifestComponent `json:"components"`
	Parameters  []ManifestParameter `json:"parameters,omitempty"`
	ImageList   []ManifestImage     `json:"imageList,omitempty"`
}

type ManifestComponent struct {
	Name                 string                 `json:"name"`
	DisplayName          string                 `json:"displayName,omitempty"`
	ComponentType        string                 `json:"componentType"`
	Chart                ChartRef               `json:"chart"`
	DefaultValues        map[string]interface{} `json:"defaultValues,omitempty"`
	Parameters           []ManifestParameter    `json:"parameters,omitempty"`
	Dependencies         []ManifestDependency   `json:"dependencies,omitempty"`
	ResourceRequirements *ResourceRequirements  `json:"resourceRequirements,omitempty"`
}

type ChartRef struct {
	Repository string `json:"repository"`
	Version    string `json:"version"`
	Name       string `json:"name,omitempty"`
}

type ManifestParameter struct {
	Name             string           `json:"name"`
	DisplayName      string           `json:"displayName,omitempty"`
	Type             string           `json:"type"`
	DefaultValue     interface{}      `json:"defaultValue,omitempty"`
	Required         bool             `json:"required,omitempty"`
	Path             string           `json:"path,omitempty"`
	Validation       *FieldValidation `json:"validation,omitempty"`
	Scope            string           `json:"scope,omitempty"`
	TargetComponents []string         `json:"targetComponents,omitempty"`
}

type ManifestDependency struct {
	Component string `json:"component"`
	Type      string `json:"type,omitempty"`
	Condition string `json:"condition,omitempty"`
}

type ResourceRequirements struct {
	CPU          string `json:"cpu,omitempty"`
	Memory       string `json:"memory,omitempty"`
	Storage      string `json:"storage,omitempty"`
	MinNodeCount int    `json:"minNodeCount,omitempty"`
}

type ManifestImage struct {
	Name      string `json:"name"`
	Image     string `json:"image"`
	Tag       string `json:"tag"`
	Digest    string `json:"digest,omitempty"`
	Component string `json:"component,omitempty"`
}

// 通用响应
type APIResponse struct {
	Code    int         `json:"code"`
	Data    interface{} `json:"data,omitempty"`
	Message string      `json:"message,omitempty"`
}

type PageRequest struct {
	PageNo   int    `json:"pageNo"`
	PageSize int    `json:"pageSize"`
	Keyword  string `json:"keyword,omitempty"`
}

type PageResult struct {
	Records  interface{} `json:"records"`
	Total    int64       `json:"total"`
	PageNo   int         `json:"pageNo"`
	PageSize int         `json:"pageSize"`
}

func Success(data interface{}) APIResponse {
	return APIResponse{Code: 0, Data: data}
}

func Error(msg string) APIResponse {
	return APIResponse{Code: 1, Message: msg}
}

// 时间辅助
func Now() string {
	return time.Now().Format("2006-01-02T15:04:05")
}

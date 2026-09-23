package v1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// =============================================================================
// Common Types
// =============================================================================

// Phase 表示资源当前的生命周期阶段。
type Phase string

const (
	PhasePending   Phase = "Pending"
	PhaseDeploying Phase = "Deploying"
	PhaseReady     Phase = "Ready"
	PhaseDegraded  Phase = "Degraded"
	PhaseFailed    Phase = "Failed"
	PhaseUnknown   Phase = "Unknown"
	PhaseDeleting  Phase = "Deleting"
	PhaseSuspended Phase = "Suspended"
)

// ConditionType 表示 condition 的类型。
type ConditionType string

const (
	ConditionAvailable     ConditionType = "Available"
	ConditionProgressing   ConditionType = "Progressing"
	ConditionDegraded      ConditionType = "Degraded"
	ConditionHelmDeployed  ConditionType = "HelmDeployed"
	ConditionHealthy       ConditionType = "Healthy"
	ConditionResourceReady ConditionType = "ResourceReady"
)

// Condition 表示资源的状态条件。
type Condition struct {
	Type               ConditionType          `json:"type"`
	Status             metav1.ConditionStatus `json:"status"`
	Reason             string                 `json:"reason,omitempty"`
	Message            string                 `json:"message,omitempty"`
	LastTransitionTime metav1.Time            `json:"lastTransitionTime"`
	ObservedGeneration int64                  `json:"observedGeneration,omitempty"`
}

// ComponentType 表示组件类型。
type ComponentType string

const (
	ComponentTypeService ComponentType = "Service"
	ComponentTypeCluster ComponentType = "Cluster"
	ComponentTypeProject ComponentType = "Project"
)

// DependencyType 表示依赖类型。
type DependencyType string

const (
	DependencyHard     DependencyType = "Hard"
	DependencySoft     DependencyType = "Soft"
	DependencyOptional DependencyType = "Optional"
)

// DependencyCondition 表示依赖满足的条件。
type DependencyCondition string

const (
	DependencyReady    DependencyCondition = "Ready"
	DependencyDeployed DependencyCondition = "Deployed"
)

// Dependency 表示组件依赖。
type Dependency struct {
	Component  string              `json:"component,omitempty"`
	ServiceRef string              `json:"serviceRef,omitempty"`
	Type       DependencyType      `json:"type,omitempty"`
	Condition  DependencyCondition `json:"condition,omitempty"`
}

// HealthCheck 表示健康检查配置。
type HealthCheck struct {
	Prometheus *PrometheusHealthCheck `json:"prometheus,omitempty"`
	HTTP       *HTTPHealthCheck       `json:"http,omitempty"`
	Pod        *PodHealthCheck        `json:"pod,omitempty"`
}

type PrometheusHealthCheck struct {
	Query    string `json:"query,omitempty"`
	Interval string `json:"interval,omitempty"`
	Timeout  string `json:"timeout,omitempty"`
}

type HTTPHealthCheck struct {
	URL           string `json:"url,omitempty"`
	Interval      string `json:"interval,omitempty"`
	Timeout       string `json:"timeout,omitempty"`
	ExpectedCodes []int  `json:"expectedCodes,omitempty"`
}

type PodHealthCheck struct {
	MinReadySeconds       int     `json:"minReadySeconds,omitempty"`
	RequiredReplicasRatio float64 `json:"requiredReplicasRatio,omitempty"`
}

// DeploymentStrategy 表示部署策略。
type DeploymentStrategy struct {
	UpdateType           string          `json:"updateType,omitempty"`
	Timeout              string          `json:"timeout,omitempty"`
	Rollback             *RollbackConfig `json:"rollback,omitempty"`
	Retry                *RetryConfig    `json:"retry,omitempty"`
	RevisionHistoryLimit *int            `json:"revisionHistoryLimit,omitempty"`
	Atomic               bool            `json:"atomic,omitempty"`
}

type RollbackConfig struct {
	Enabled      bool `json:"enabled,omitempty"`
	MaxRetries   int  `json:"maxRetries,omitempty"`
	WaitForReady bool `json:"waitForReady,omitempty"`
}

type RetryConfig struct {
	MaxRetries      int    `json:"maxRetries,omitempty"`
	BackoffStrategy string `json:"backoffStrategy,omitempty"`
}

// ClusterAffinity 表示多集群分发配置。
type ClusterAffinity struct {
	ClusterLabelSelector map[string]string `json:"clusterLabelSelector,omitempty"`
	Placement            string            `json:"placement,omitempty"`
}

// HistoryEntry 表示审计历史条目。
type HistoryEntry struct {
	Revision          int                    `json:"revision,omitempty"`
	Version           string                 `json:"version,omitempty"`
	Operation         string                 `json:"operation,omitempty"`
	Operator          string                 `json:"operator,omitempty"`
	Message           string                 `json:"message,omitempty"`
	ParameterSnapshot map[string]interface{} `json:"parameterSnapshot,omitempty"`
	Timestamp         metav1.Time            `json:"timestamp,omitempty"`
}

// ResourceRequirements 表示工堪资源需求。
type ResourceRequirements struct {
	CPU          string `json:"cpu,omitempty"`
	Memory       string `json:"memory,omitempty"`
	Storage      string `json:"storage,omitempty"`
	MinNodeCount int    `json:"minNodeCount,omitempty"`
}

// ParameterSchema 表示参数 schema 定义。
type ParameterSchema struct {
	Name        string `json:"name,omitempty"`
	DisplayName string `json:"displayName,omitempty"`
	Description string `json:"description,omitempty"`
	Type        string `json:"type,omitempty"`
	// DefaultValue 是参数默认值，具体类型由 Type 决定（string/integer/boolean/array/object）。
	// 没有 Schemaless 标记时 controller-gen 会生成空的 `defaultValue: {}`，
	// 缺 type 的 schema 会被 API Server 拒收，整份 CRD apply 不上去。
	// +kubebuilder:validation:Schemaless
	// +kubebuilder:pruning:PreserveUnknownFields
	DefaultValue     interface{}          `json:"defaultValue,omitempty"`
	Required         bool                 `json:"required,omitempty"`
	Path             string               `json:"path,omitempty"`
	Validation       *ParameterValidation `json:"validation,omitempty"`
	Scope            string               `json:"scope,omitempty"`
	TargetComponents []string             `json:"targetComponents,omitempty"`
}

type ParameterValidation struct {
	Pattern   string   `json:"pattern,omitempty"`
	MinLength *int     `json:"minLength,omitempty"`
	MaxLength *int     `json:"maxLength,omitempty"`
	Minimum   *float64 `json:"minimum,omitempty"`
	Maximum   *float64 `json:"maximum,omitempty"`
	Enum      []string `json:"enum,omitempty"`
}

// GlobalParameters 表示全局参数。
type GlobalParameters struct {
	ImageRegistry       string `json:"imageRegistry,omitempty"`
	DefaultStorageClass string `json:"defaultStorageClass,omitempty"`
	DomainSuffix        string `json:"domainSuffix,omitempty"`
}

// ImageSpec 表示镜像清单条目。
type ImageSpec struct {
	Name      string `json:"name,omitempty"`
	Image     string `json:"image,omitempty"`
	Tag       string `json:"tag,omitempty"`
	Digest    string `json:"digest,omitempty"`
	Component string `json:"component,omitempty"`
}

// =============================================================================
// Pod 级明细与差异（v0.3，对照 AAP AppInstance.status 吸收）
// =============================================================================

// PodDetail 是单个 Pod 的排障明细。
type PodDetail struct {
	// Name 是 Pod 名称。
	Name string `json:"name,omitempty"`
	// Namespace 是 Pod 所在命名空间。
	Namespace string `json:"namespace,omitempty"`
	// IP 是 Pod IP。
	IP string `json:"ip,omitempty"`
	// HostIP 是宿主机 IP。
	HostIP string `json:"hostIP,omitempty"`
	// WorkloadKind 是所属 workload 的 Kind。
	WorkloadKind string `json:"workloadKind,omitempty"`
	// WorkloadName 是所属 workload 的名称。
	WorkloadName string `json:"workloadName,omitempty"`
	// Ready 表示所有容器是否就绪。
	Ready bool `json:"ready,omitempty"`
	// Phase 是 Pod phase（Pending/Running/Succeeded/Failed）。
	Phase string `json:"phase,omitempty"`
	// State 是容器 state 摘要，如 Running / CrashLoopBackOff / Completed。
	State string `json:"state,omitempty"`
	// Restarts 是容器重启总次数。
	Restarts int `json:"restarts,omitempty"`
	// UpdatedRevision 表示该 Pod 是否已切到当前修订。
	UpdatedRevision bool `json:"updatedRevision,omitempty"`
	// Message 是补充说明（如未就绪原因）。
	Message string `json:"message,omitempty"`
}

// WorkloadDiff 是期望值与实际值的差异。
type WorkloadDiff struct {
	// Kind 是 workload 类型。
	Kind string `json:"kind,omitempty"`
	// Name 是 workload 名称。
	Name string `json:"name,omitempty"`
	// Namespace 是命名空间。
	Namespace string `json:"namespace,omitempty"`
	// Path 是差异字段路径，如 .spec.replicas。
	Path string `json:"path,omitempty"`
	// Expected 是期望值。
	Expected string `json:"expected,omitempty"`
	// Current 是当前实际值。
	Current string `json:"current,omitempty"`
	// Message 是差异说明。
	Message string `json:"message,omitempty"`
}

// =============================================================================
// 引用与持久化（v0.3）
// =============================================================================

// RefResource 引用集群里已存在的对象，只断言其存在，不参与排序。
type RefResource struct {
	// Group 是 API group。
	Group string `json:"group,omitempty"`
	// Version 是 API version。
	Version string `json:"version,omitempty"`
	// Kind 是资源类型。
	Kind string `json:"kind"`
	// Name 是资源名称。
	Name string `json:"name"`
	// Namespace 是命名空间，集群级对象留空。
	Namespace string `json:"namespace,omitempty"`
	// ClusterScoped 表示是否为集群级对象。
	ClusterScoped bool `json:"clusterScoped,omitempty"`
}

// PersistentVolumeConfig 是组件的持久化配置。
type PersistentVolumeConfig struct {
	// VolumeName 是卷名。
	VolumeName string `json:"volumeName,omitempty"`
	// StorageClass 是存储类。
	StorageClass string `json:"storageClass,omitempty"`
	// Size 是容量，如 10Gi。
	Size string `json:"size,omitempty"`
	// AccessModes 是访问模式。
	AccessModes []string `json:"accessModes,omitempty"`
	// Retain 表示卸载时保留 PVC（打 helm.sh/resource-policy: keep）。
	Retain bool `json:"retain,omitempty"`
	// UseEmptyDir 表示不使用 PVC，改用 emptyDir。
	UseEmptyDir bool `json:"useEmptyDir,omitempty"`
	// ReuseFromRelease 表示从哪一次发布复用已有 PVC（按 release-id 标签匹配）。
	ReuseFromRelease string `json:"reuseFromRelease,omitempty"`
}

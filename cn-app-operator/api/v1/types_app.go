package v1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// =============================================================================
// App CRD Types
// =============================================================================

// AppSpec 定义了 App 的期望状态。
type AppSpec struct {
	// DisplayName 是应用显示名称。
	DisplayName string `json:"displayName"`
	// Description 是应用描述。
	Description string `json:"description,omitempty"`
	// Version 是应用版本标识。
	Version string `json:"version,omitempty"`
	// ReleaseID 是这一次发布的关联号，下属 CloudService/CloudComponent 与 ProductTask 与之对齐。
	ReleaseID string `json:"releaseID,omitempty"`
	// PlanRevision 是现场规划修订号，用于追溯这一次发布用的是哪一版现场规划。
	PlanRevision string `json:"planRevision,omitempty"`
	// Services 是关联的 CloudService 列表。
	Services []ServiceRef `json:"services,omitempty"`
	// GlobalParameters 是跨所有服务生效的全局参数。
	GlobalParameters *GlobalParameters `json:"globalParameters,omitempty"`
	// TerminationPolicy 是删除 App 时的级联策略。
	// +kubebuilder:validation:Enum=Delete;Orphan
	// +kubebuilder:default=Delete
	TerminationPolicy string `json:"terminationPolicy,omitempty"`
}

// ServiceRef 表示对 CloudService 的引用。
type ServiceRef struct {
	// Name 是 CloudService 名称（同一 namespace）。
	Name string `json:"name"`
	// Alias 是可选别名。
	Alias string `json:"alias,omitempty"`
	// Parameters 是服务级参数覆盖。
	Parameters map[string]interface{} `json:"parameters,omitempty"`
}

// AppStatus 定义了 App 的当前状态。
type AppStatus struct {
	// Phase 是总体阶段。
	Phase Phase `json:"phase,omitempty"`
	// AggregatedSummary 是聚合摘要。
	AggregatedSummary *AggregatedSummary `json:"aggregatedSummary,omitempty"`
	// ServiceStatuses 是每个下属服务的简要状态。
	ServiceStatuses []ServiceStatus `json:"serviceStatuses,omitempty"`
	// ObservedReleaseID 是当前实际生效的发布号，从下属 CloudComponent 收敛。
	ObservedReleaseID string `json:"observedReleaseID,omitempty"`
	// Conditions 是标准化条件。
	Conditions []Condition `json:"conditions,omitempty"`
	// ObservedGeneration 是最后处理的 spec 版本。
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`
	// History 是操作审计历史。
	History []HistoryEntry `json:"history,omitempty"`
}

type AggregatedSummary struct {
	TotalServices    int `json:"totalServices,omitempty"`
	ReadyServices    int `json:"readyServices,omitempty"`
	DegradedServices int `json:"degradedServices,omitempty"`
	FailedServices   int `json:"failedServices,omitempty"`
	TotalComponents  int `json:"totalComponents,omitempty"`
	ReadyComponents  int `json:"readyComponents,omitempty"`
}

type ServiceStatus struct {
	Name              string       `json:"name,omitempty"`
	Phase             Phase        `json:"phase,omitempty"`
	ReleaseID         string       `json:"releaseID,omitempty"`
	ObservedReleaseID string       `json:"observedReleaseID,omitempty"`
	ErrorMessage      string       `json:"errorMessage,omitempty"`
	LastUpdateTime    *metav1.Time `json:"lastUpdateTime,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object
// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="Display Name",type=string,JSONPath=`.spec.displayName`
// +kubebuilder:printcolumn:name="Status",type=string,JSONPath=`.status.phase`
// +kubebuilder:printcolumn:name="Services",type=integer,JSONPath=`.status.aggregatedSummary.totalServices`
// +kubebuilder:printcolumn:name="Ready",type=integer,JSONPath=`.status.aggregatedSummary.readyServices`
// +kubebuilder:printcolumn:name="Age",type=date,JSONPath=`.metadata.creationTimestamp`

// App 是用户面向的顶层聚合资源。
type App struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`
	Spec              AppSpec   `json:"spec,omitempty"`
	Status            AppStatus `json:"status,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// AppList 包含 App 列表。
type AppList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []App `json:"items"`
}

func init() {
	SchemeBuilder.Register(&App{}, &AppList{})
}

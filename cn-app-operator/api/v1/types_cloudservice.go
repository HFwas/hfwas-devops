package v1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// =============================================================================
// CloudService CRD Types
// =============================================================================

// CloudServiceSpec 定义了 CloudService 的期望状态。
type CloudServiceSpec struct {
	// DisplayName 是产品显示名称。
	DisplayName string `json:"displayName"`
	// Version 是 CloudService 版本号。
	Version string `json:"version,omitempty"`
	// Description 是产品描述。
	Description string `json:"description,omitempty"`
	// Icon 是图标 URL。
	Icon string `json:"icon,omitempty"`
	// Components 是组件定义列表。
	Components []ComponentDef `json:"components"`
	// Parameters 是产品级参数覆盖。
	Parameters []ParameterSchema `json:"parameters,omitempty"`
	// ImageList 是镜像清单。
	ImageList []ImageSpec `json:"imageList,omitempty"`
	// ReleaseID 是本服务所属的发布号，由上级 App 下发。
	ReleaseID string `json:"releaseID,omitempty"`
}

// ComponentDef 是 CloudService 中的组件定义。
type ComponentDef struct {
	// Name 是组件名称（在 CloudService 内唯一）。
	Name string `json:"name"`
	// DisplayName 是组件显示名称。
	DisplayName string `json:"displayName,omitempty"`
	// ComponentType 是组件类型。
	ComponentType ComponentType `json:"componentType"`
	// Chart 是 Helm Chart 引用。
	Chart ChartRef `json:"chart"`
	// DefaultValues 是组件默认 values。
	DefaultValues map[string]interface{} `json:"defaultValues,omitempty"`
	// Parameters 是该组件暴露的参数 schema。
	Parameters []ParameterSchema `json:"parameters,omitempty"`
	// Dependencies 是组件依赖声明。
	Dependencies []Dependency `json:"dependencies,omitempty"`
	// HealthCheck 是健康检查配置。
	HealthCheck *HealthCheck `json:"healthCheck,omitempty"`
	// DeploymentStrategy 是部署策略。
	DeploymentStrategy *DeploymentStrategy `json:"deploymentStrategy,omitempty"`
	// ClusterAffinity 是多集群分发配置。
	ClusterAffinity *ClusterAffinity `json:"clusterAffinity,omitempty"`
	// ResourceRequirements 是资源需求（工堪用）。
	ResourceRequirements *ResourceRequirements `json:"resourceRequirements,omitempty"`
}

// ChartRef 表示 Helm Chart 引用。
type ChartRef struct {
	// Repository 是 Chart 仓库 URL 或 OCI 引用。
	Repository string `json:"repository"`
	// Version 是 Chart 版本号。
	Version string `json:"version"`
	// Name 是 Helm Chart 名称。
	Name string `json:"name,omitempty"`
}

// CloudServiceStatus 定义了 CloudService 的当前状态。
type CloudServiceStatus struct {
	// Phase 是总体阶段。
	Phase Phase `json:"phase,omitempty"`
	// ComponentCount 是组件总数。
	ComponentCount int `json:"componentCount,omitempty"`
	// ReadyCount 是已就绪组件数。
	ReadyCount int `json:"readyCount,omitempty"`
	// ComponentStatuses 是每个组件的状态摘要。
	ComponentStatuses []ComponentStatus `json:"componentStatuses,omitempty"`
	// ObservedVersion 是当前生效的版本。
	ObservedVersion string `json:"observedVersion,omitempty"`
	// ObservedReleaseID 是实际观察到的发布号。
	ObservedReleaseID string `json:"observedReleaseID,omitempty"`
	// ReconciliationOrder 是按依赖排序后的组件名称列表。
	ReconciliationOrder []string `json:"reconciliationOrder,omitempty"`
	// Conditions 是标准化条件。
	Conditions []Condition `json:"conditions,omitempty"`
	// ObservedGeneration 是最后处理的 spec 版本。
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`
	// History 是版本历史。
	History []CloudServiceHistoryEntry `json:"history,omitempty"`
}

type ComponentStatus struct {
	Name               string       `json:"name,omitempty"`
	Phase              Phase        `json:"phase,omitempty"`
	ReleaseID          string       `json:"releaseID,omitempty"`
	ObservedReleaseID  string       `json:"observedReleaseID,omitempty"`
	ChartVersion       string       `json:"chartVersion,omitempty"`
	DeployedVersion    string       `json:"deployedVersion,omitempty"`
	ErrorMessage       string       `json:"errorMessage,omitempty"`
	LastTransitionTime *metav1.Time `json:"lastTransitionTime,omitempty"`
}

type CloudServiceHistoryEntry struct {
	Revision          int                    `json:"revision,omitempty"`
	Version           string                 `json:"version,omitempty"`
	Operation         string                 `json:"operation,omitempty"`
	ComponentChanges  []ComponentChange      `json:"componentChanges,omitempty"`
	ParameterSnapshot map[string]interface{} `json:"parameterSnapshot,omitempty"`
	Operator          string                 `json:"operator,omitempty"`
	Timestamp         metav1.Time            `json:"timestamp,omitempty"`
}

type ComponentChange struct {
	Component  string `json:"component,omitempty"`
	OldVersion string `json:"oldVersion,omitempty"`
	NewVersion string `json:"newVersion,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object
// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="Display Name",type=string,JSONPath=`.spec.displayName`
// +kubebuilder:printcolumn:name="Status",type=string,JSONPath=`.status.phase`
// +kubebuilder:printcolumn:name="Components",type=integer,JSONPath=`.status.componentCount`
// +kubebuilder:printcolumn:name="Ready",type=integer,JSONPath=`.status.readyCount`
// +kubebuilder:printcolumn:name="Age",type=date,JSONPath=`.metadata.creationTimestamp`

// CloudService 定义了一个产品/云服务的完整蓝图。
type CloudService struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`
	Spec              CloudServiceSpec   `json:"spec,omitempty"`
	Status            CloudServiceStatus `json:"status,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CloudServiceList 包含 CloudService 列表。
type CloudServiceList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []CloudService `json:"items"`
}

func init() {
	SchemeBuilder.Register(&CloudService{}, &CloudServiceList{})
}

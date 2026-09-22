package v1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// =============================================================================
// CloudComponent CRD Types
// =============================================================================

// CloudComponentSpec 定义了 CloudComponent 的期望状态。
type CloudComponentSpec struct {
	// DisplayName 是显示名称。
	DisplayName string `json:"displayName,omitempty"`
	// ComponentType 是组件类型。
	ComponentType ComponentType `json:"componentType"`
	// ServiceRef 是所属 CloudService 名称。
	ServiceRef string `json:"serviceRef,omitempty"`
	// ChartName 是 Chart 名称。
	ChartName string `json:"chartName"`
	// ChartVersion 是 Chart 版本号。
	ChartVersion string `json:"chartVersion"`
	// ChartRepo 是 Chart 仓库 URL。
	ChartRepo string `json:"chartRepo,omitempty"`
	// ChartRepoName 是仓库别名。
	ChartRepoName string `json:"chartRepoName,omitempty"`
	// ReleaseName 是 Helm release 名称（默认自动生成）。
	ReleaseName string `json:"releaseName,omitempty"`
	// TargetNamespace 是 Helm release 部署的目标命名空间。
	TargetNamespace string `json:"targetNamespace,omitempty"`
	// Values 是合并后的完整 values。
	Values map[string]interface{} `json:"values,omitempty"`
	// ValuesOverlay 是上层传入的参数覆盖。
	ValuesOverlay map[string]interface{} `json:"valuesOverlay,omitempty"`
	// DeploymentStrategy 是部署策略。
	DeploymentStrategy *DeploymentStrategy `json:"deploymentStrategy,omitempty"`
	// Dependencies 是组件依赖。
	Dependencies []Dependency `json:"dependencies,omitempty"`
	// HealthCheck 是健康检查配置。
	HealthCheck *HealthCheck `json:"healthCheck,omitempty"`
	// ClusterAffinity 是多集群分发配置。
	ClusterAffinity *ClusterAffinity `json:"clusterAffinity,omitempty"`
	// ReleaseID 是本组件所属的发布号，由上级下发。
	ReleaseID string `json:"releaseID,omitempty"`
	// RefResources 引用集群里已存在的对象（只断言存在，不参与排序）。
	RefResources []RefResource `json:"refResources,omitempty"`
	// PersistentVolumeConfigs 是持久化配置（retain / 跨发布复用）。
	PersistentVolumeConfigs []PersistentVolumeConfig `json:"persistentVolumeConfigs,omitempty"`
}

// CloudComponentStatus 定义了 CloudComponent 的当前状态。
type CloudComponentStatus struct {
	// Phase 是组件阶段。
	Phase Phase `json:"phase,omitempty"`
	// HelmReleaseName 是实际的 Helm release 名称。
	HelmReleaseName string `json:"helmReleaseName,omitempty"`
	// HelmStatus 是 Helm release 状态。
	HelmStatus string `json:"helmStatus,omitempty"`
	// HelmRevision 是 Helm release revision 号。
	HelmRevision int `json:"helmRevision,omitempty"`
	// ChartVersion 是当前已部署的 chart 版本。
	ChartVersion string `json:"chartVersion,omitempty"`
	// ObservedChartVersion 是实际 chart 版本。
	ObservedChartVersion string `json:"observedChartVersion,omitempty"`
	// WorkloadStatus 是 Workload 状态聚合。
	WorkloadStatus *WorkloadStatus `json:"workloadStatus,omitempty"`
	// ObservedReleaseID 是实际观察到的发布号。
	ObservedReleaseID string `json:"observedReleaseID,omitempty"`
	// PodsDetail 是每个 Pod 的排障明细。
	PodsDetail []PodDetail `json:"podsDetail,omitempty"`
	// WorkloadDiffs 是期望值与实际值的差异。
	WorkloadDiffs []WorkloadDiff `json:"workloadDiffs,omitempty"`
	// InstallCount 是安装计数。
	InstallCount int `json:"installCount,omitempty"`
	// UpgradeCount 是升级计数。
	UpgradeCount int `json:"upgradeCount,omitempty"`
	// RollbackCount 是回滚计数。
	RollbackCount int `json:"rollbackCount,omitempty"`
	// FailureMessage 是失败信息。
	FailureMessage string `json:"failureMessage,omitempty"`
	// FailureReason 是失败原因。
	FailureReason string `json:"failureReason,omitempty"`
	// RetryCount 是重试次数。
	RetryCount int `json:"retryCount,omitempty"`
	// LastRetryTime 是最后重试时间。
	LastRetryTime *metav1.Time `json:"lastRetryTime,omitempty"`
	// Conditions 是标准化条件。
	Conditions []Condition `json:"conditions,omitempty"`
	// ObservedGeneration 是最后处理的 spec 版本。
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`
	// LastAppliedRevision 是最后应用的 ControllerRevision 名称。
	LastAppliedRevision string `json:"lastAppliedRevision,omitempty"`
	// CurrentRevision 是当前 ControllerRevision 名称。
	CurrentRevision string `json:"currentRevision,omitempty"`
	// HealthCheckResult 是健康检查结果。
	HealthCheckResult *HealthCheckResult `json:"healthCheckResult,omitempty"`
	// History 是组件审计历史。
	History []ComponentHistoryEntry `json:"history,omitempty"`
}

type WorkloadStatus struct {
	TotalWorkloads    int            `json:"totalWorkloads,omitempty"`
	ReadyWorkloads    int            `json:"readyWorkloads,omitempty"`
	DegradedWorkloads int            `json:"degradedWorkloads,omitempty"`
	Workloads         []WorkloadInfo `json:"workloads,omitempty"`
}

type WorkloadInfo struct {
	Kind       string `json:"kind,omitempty"`
	Name       string `json:"name,omitempty"`
	Namespace  string `json:"namespace,omitempty"`
	APIVersion string `json:"apiVersion,omitempty"`
	Ready      bool   `json:"ready,omitempty"`
	Status     string `json:"status,omitempty"`
}

type HealthCheckResult struct {
	Healthy       bool         `json:"healthy,omitempty"`
	LastCheckTime *metav1.Time `json:"lastCheckTime,omitempty"`
	Message       string       `json:"message,omitempty"`
}

type ComponentHistoryEntry struct {
	Revision       int          `json:"revision,omitempty"`
	Operation      string       `json:"operation,omitempty"`
	ChartVersion   string       `json:"chartVersion,omitempty"`
	ValuesHash     string       `json:"valuesHash,omitempty"`
	Message        string       `json:"message,omitempty"`
	Operator       string       `json:"operator,omitempty"`
	StartTime      *metav1.Time `json:"startTime,omitempty"`
	CompletionTime *metav1.Time `json:"completionTime,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object
// +kubebuilder:object:root=true
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="Status",type=string,JSONPath=`.status.phase`
// +kubebuilder:printcolumn:name="Component Type",type=string,JSONPath=`.spec.componentType`
// +kubebuilder:printcolumn:name="Chart Version",type=string,JSONPath=`.spec.chartVersion`
// +kubebuilder:printcolumn:name="Helm Release",type=string,JSONPath=`.status.helmReleaseName`
// +kubebuilder:printcolumn:name="Age",type=date,JSONPath=`.metadata.creationTimestamp`

// CloudComponent 对应一个 Helm Chart 的部署实例。
type CloudComponent struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`
	Spec              CloudComponentSpec   `json:"spec,omitempty"`
	Status            CloudComponentStatus `json:"status,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// CloudComponentList 包含 CloudComponent 列表。
type CloudComponentList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []CloudComponent `json:"items"`
}

func init() {
	SchemeBuilder.Register(&CloudComponent{}, &CloudComponentList{})
}

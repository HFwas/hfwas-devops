package v1

import (
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
)

// =============================================================================
// ProductTask CRD Types
//
// 集群级资源。表示某一次发布（releaseID）上的一个运维步骤，用来表达顺序
// ——例如「init job 先跑完，网关再起」——而不是把顺序写进各个 workload。
//
// 与依赖 DAG（CloudComponent.spec.dependencies）的分工：
//   - 依赖 DAG：组件 ↔ 组件，同 namespace，只等待
//   - ProductTask：任意步骤，可作用于任意 K8s 对象，集群级，可跨 namespace
//
// 设计来源：docs/research/aap-product-operator 四类 CRD.md
// =============================================================================

// TaskPhase 表示 ProductTask 的阶段。
type TaskPhase string

const (
	// TaskPhasePending 表示等待前置步骤完成。
	TaskPhasePending TaskPhase = "Pending"
	// TaskPhaseRunning 表示正在执行。
	TaskPhaseRunning TaskPhase = "Running"
	// TaskPhaseSucceeded 表示已完成。
	TaskPhaseSucceeded TaskPhase = "Succeeded"
	// TaskPhaseFailed 表示失败。
	TaskPhaseFailed TaskPhase = "Failed"
)

// TaskResource 是任务作用的 K8s 对象。
type TaskResource struct {
	// Group 是 API group。
	Group string `json:"group,omitempty"`
	// Version 是 API version。
	Version string `json:"version,omitempty"`
	// Kind 是资源类型。为 Job 时控制器会读其完成状态。
	Kind string `json:"kind,omitempty"`
	// Name 是资源名称。
	Name string `json:"name,omitempty"`
	// Namespace 是命名空间。
	Namespace string `json:"namespace,omitempty"`
}

// TaskComponentRef 指向关联的 CloudComponent，控制器据此对组件做顺序放行。
type TaskComponentRef struct {
	// ServiceRef 是所属 CloudService 名称。
	ServiceRef string `json:"serviceRef,omitempty"`
	// Component 是 CloudComponent 名称。
	Component string `json:"component,omitempty"`
	// Namespace 是 CloudComponent 所在命名空间，留空表示与任务作用对象同 namespace。
	Namespace string `json:"namespace,omitempty"`
}

// TaskOrder 按 task 名称声明本步骤与其他步骤的先后关系。
type TaskOrder struct {
	// Before 是本步骤要排在哪些步骤之前。
	Before []string `json:"before,omitempty"`
	// After 是本步骤要排在哪些步骤之后。
	After []string `json:"after,omitempty"`
}

// ProductTaskSpec 定义了 ProductTask 的期望状态。
type ProductTaskSpec struct {
	// ReleaseID 属于哪一次发布，与 App.spec.releaseID 取同一个值。
	ReleaseID string `json:"releaseID"`
	// OpsType 是这一步要做的操作。按 v0.3 的 enum 策略保持自由字符串。
	OpsType string `json:"opsType,omitempty"`
	// Resource 是作用对象，可为空（此时只作顺序标记）。
	Resource *TaskResource `json:"resource,omitempty"`
	// RefComponent 是关联的 CloudComponent。
	RefComponent *TaskComponentRef `json:"refComponent,omitempty"`
	// TaskOrder 是本步骤的先后声明。
	TaskOrder *TaskOrder `json:"taskOrder,omitempty"`
}

// TaskMessage 是阶段迁移记录。
type TaskMessage struct {
	// Message 是说明。
	Message string `json:"message,omitempty"`
	// LastTransitionTime 是发生时间。
	LastTransitionTime metav1.Time `json:"lastTransitionTime,omitempty"`
}

// ProductTaskStatus 定义了 ProductTask 的当前状态。
type ProductTaskStatus struct {
	// Phase 是任务阶段。
	Phase TaskPhase `json:"phase,omitempty"`
	// Messages 是阶段迁移记录。
	Messages []TaskMessage `json:"messages,omitempty"`
	// Conditions 是标准化条件。
	Conditions []Condition `json:"conditions,omitempty"`
	// ObservedGeneration 是最后处理的 spec 版本。
	ObservedGeneration int64 `json:"observedGeneration,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object
// +kubebuilder:object:root=true
// +kubebuilder:resource:scope=Cluster,shortName=pt;ptask
// +kubebuilder:subresource:status
// +kubebuilder:printcolumn:name="Release",type=string,JSONPath=`.spec.releaseID`
// +kubebuilder:printcolumn:name="OpsType",type=string,JSONPath=`.spec.opsType`
// +kubebuilder:printcolumn:name="Target",type=string,JSONPath=`.spec.resource.name`
// +kubebuilder:printcolumn:name="Phase",type=string,JSONPath=`.status.phase`
// +kubebuilder:printcolumn:name="Age",type=date,JSONPath=`.metadata.creationTimestamp`

// ProductTask 是集群级的发布步骤资源。
type ProductTask struct {
	metav1.TypeMeta   `json:",inline"`
	metav1.ObjectMeta `json:"metadata,omitempty"`
	Spec              ProductTaskSpec   `json:"spec,omitempty"`
	Status            ProductTaskStatus `json:"status,omitempty"`
}

// +k8s:deepcopy-gen:interfaces=k8s.io/apimachinery/pkg/runtime.Object

// ProductTaskList 包含 ProductTask 列表。
type ProductTaskList struct {
	metav1.TypeMeta `json:",inline"`
	metav1.ListMeta `json:"metadata,omitempty"`
	Items           []ProductTask `json:"items"`
}

func init() {
	SchemeBuilder.Register(&ProductTask{}, &ProductTaskList{})
}

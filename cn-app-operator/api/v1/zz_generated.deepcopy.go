package v1

import "k8s.io/apimachinery/pkg/runtime"

// 手动维护的 DeepCopy 方法。
// 生产环境建议使用 `controller-gen object:headerFile=hack/boilerplate.go.txt paths=./api/...` 自动生成。

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *App) DeepCopyInto(out *App) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
}

// DeepCopy 返回 App 的深拷贝。
func (in *App) DeepCopy() *App {
	if in == nil {
		return nil
	}
	out := new(App)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *App) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *AppList) DeepCopyInto(out *AppList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ListMeta.DeepCopyInto(&out.ListMeta)
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]App, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

// DeepCopy 返回 AppList 的深拷贝。
func (in *AppList) DeepCopy() *AppList {
	if in == nil {
		return nil
	}
	out := new(AppList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *AppList) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *CloudService) DeepCopyInto(out *CloudService) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
}

// DeepCopy 返回 CloudService 的深拷贝。
func (in *CloudService) DeepCopy() *CloudService {
	if in == nil {
		return nil
	}
	out := new(CloudService)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *CloudService) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *CloudServiceList) DeepCopyInto(out *CloudServiceList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ListMeta.DeepCopyInto(&out.ListMeta)
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]CloudService, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

// DeepCopy 返回 CloudServiceList 的深拷贝。
func (in *CloudServiceList) DeepCopy() *CloudServiceList {
	if in == nil {
		return nil
	}
	out := new(CloudServiceList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *CloudServiceList) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *CloudComponent) DeepCopyInto(out *CloudComponent) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
}

// DeepCopy 返回 CloudComponent 的深拷贝。
func (in *CloudComponent) DeepCopy() *CloudComponent {
	if in == nil {
		return nil
	}
	out := new(CloudComponent)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *CloudComponent) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 将 src 的内容复制到 dst。
func (in *CloudComponentList) DeepCopyInto(out *CloudComponentList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ListMeta.DeepCopyInto(&out.ListMeta)
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]CloudComponent, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

// DeepCopy 返回 CloudComponentList 的深拷贝。
func (in *CloudComponentList) DeepCopy() *CloudComponentList {
	if in == nil {
		return nil
	}
	out := new(CloudComponentList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 实现 runtime.Object 接口。
func (in *CloudComponentList) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto — AppSpec
func (in *AppSpec) DeepCopyInto(out *AppSpec) {
	*out = *in
	if in.Services != nil {
		in, out := &in.Services, &out.Services
		*out = make([]ServiceRef, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.GlobalParameters != nil {
		in, out := &in.GlobalParameters, &out.GlobalParameters
		*out = new(GlobalParameters)
		**out = **in
	}
}

func (in *AppSpec) DeepCopy() *AppSpec {
	if in == nil {
		return nil
	}
	out := new(AppSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ServiceRef
func (in *ServiceRef) DeepCopyInto(out *ServiceRef) {
	*out = *in
	if in.Parameters != nil {
		in, out := &in.Parameters, &out.Parameters
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
}

func (in *ServiceRef) DeepCopy() *ServiceRef {
	if in == nil {
		return nil
	}
	out := new(ServiceRef)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — AppStatus
func (in *AppStatus) DeepCopyInto(out *AppStatus) {
	*out = *in
	if in.AggregatedSummary != nil {
		in, out := &in.AggregatedSummary, &out.AggregatedSummary
		*out = new(AggregatedSummary)
		**out = **in
	}
	if in.ServiceStatuses != nil {
		in, out := &in.ServiceStatuses, &out.ServiceStatuses
		*out = make([]ServiceStatus, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.Conditions != nil {
		in, out := &in.Conditions, &out.Conditions
		*out = make([]Condition, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.History != nil {
		in, out := &in.History, &out.History
		*out = make([]HistoryEntry, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

func (in *AppStatus) DeepCopy() *AppStatus {
	if in == nil {
		return nil
	}
	out := new(AppStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ServiceStatus
func (in *ServiceStatus) DeepCopyInto(out *ServiceStatus) {
	*out = *in
	if in.LastUpdateTime != nil {
		in, out := &in.LastUpdateTime, &out.LastUpdateTime
		*out = (*in).DeepCopy()
	}
}

func (in *ServiceStatus) DeepCopy() *ServiceStatus {
	if in == nil {
		return nil
	}
	out := new(ServiceStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — Condition
func (in *Condition) DeepCopyInto(out *Condition) {
	*out = *in
	in.LastTransitionTime.DeepCopyInto(&out.LastTransitionTime)
}

func (in *Condition) DeepCopy() *Condition {
	if in == nil {
		return nil
	}
	out := new(Condition)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — CloudServiceSpec
func (in *CloudServiceSpec) DeepCopyInto(out *CloudServiceSpec) {
	*out = *in
	if in.Components != nil {
		in, out := &in.Components, &out.Components
		*out = make([]ComponentDef, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.Parameters != nil {
		in, out := &in.Parameters, &out.Parameters
		*out = make([]ParameterSchema, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.ImageList != nil {
		in, out := &in.ImageList, &out.ImageList
		*out = make([]ImageSpec, len(*in))
		copy(*out, *in)
	}
}

func (in *CloudServiceSpec) DeepCopy() *CloudServiceSpec {
	if in == nil {
		return nil
	}
	out := new(CloudServiceSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ComponentDef
func (in *ComponentDef) DeepCopyInto(out *ComponentDef) {
	*out = *in
	out.Chart = in.Chart
	if in.DefaultValues != nil {
		in, out := &in.DefaultValues, &out.DefaultValues
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
	if in.Parameters != nil {
		in, out := &in.Parameters, &out.Parameters
		*out = make([]ParameterSchema, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.Dependencies != nil {
		in, out := &in.Dependencies, &out.Dependencies
		*out = make([]Dependency, len(*in))
		copy(*out, *in)
	}
	if in.HealthCheck != nil {
		in, out := &in.HealthCheck, &out.HealthCheck
		*out = new(HealthCheck)
		**out = **in
	}
	if in.DeploymentStrategy != nil {
		in, out := &in.DeploymentStrategy, &out.DeploymentStrategy
		*out = new(DeploymentStrategy)
		**out = **in
	}
	if in.ClusterAffinity != nil {
		in, out := &in.ClusterAffinity, &out.ClusterAffinity
		*out = new(ClusterAffinity)
		**out = **in
	}
	if in.ResourceRequirements != nil {
		in, out := &in.ResourceRequirements, &out.ResourceRequirements
		*out = new(ResourceRequirements)
		**out = **in
	}
}

func (in *ComponentDef) DeepCopy() *ComponentDef {
	if in == nil {
		return nil
	}
	out := new(ComponentDef)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ParameterSchema
func (in *ParameterSchema) DeepCopyInto(out *ParameterSchema) {
	*out = *in
	if in.Validation != nil {
		in, out := &in.Validation, &out.Validation
		*out = new(ParameterValidation)
		**out = **in
	}
	if in.TargetComponents != nil {
		in, out := &in.TargetComponents, &out.TargetComponents
		*out = make([]string, len(*in))
		copy(*out, *in)
	}
}

func (in *ParameterSchema) DeepCopy() *ParameterSchema {
	if in == nil {
		return nil
	}
	out := new(ParameterSchema)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — CloudServiceStatus
func (in *CloudServiceStatus) DeepCopyInto(out *CloudServiceStatus) {
	*out = *in
	if in.ComponentStatuses != nil {
		in, out := &in.ComponentStatuses, &out.ComponentStatuses
		*out = make([]ComponentStatus, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.ReconciliationOrder != nil {
		in, out := &in.ReconciliationOrder, &out.ReconciliationOrder
		*out = make([]string, len(*in))
		copy(*out, *in)
	}
	if in.Conditions != nil {
		in, out := &in.Conditions, &out.Conditions
		*out = make([]Condition, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.History != nil {
		in, out := &in.History, &out.History
		*out = make([]CloudServiceHistoryEntry, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

func (in *CloudServiceStatus) DeepCopy() *CloudServiceStatus {
	if in == nil {
		return nil
	}
	out := new(CloudServiceStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ComponentStatus
func (in *ComponentStatus) DeepCopyInto(out *ComponentStatus) {
	*out = *in
	if in.LastTransitionTime != nil {
		in, out := &in.LastTransitionTime, &out.LastTransitionTime
		*out = (*in).DeepCopy()
	}
}

func (in *ComponentStatus) DeepCopy() *ComponentStatus {
	if in == nil {
		return nil
	}
	out := new(ComponentStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — CloudServiceHistoryEntry
func (in *CloudServiceHistoryEntry) DeepCopyInto(out *CloudServiceHistoryEntry) {
	*out = *in
	if in.ComponentChanges != nil {
		in, out := &in.ComponentChanges, &out.ComponentChanges
		*out = make([]ComponentChange, len(*in))
		copy(*out, *in)
	}
	if in.ParameterSnapshot != nil {
		in, out := &in.ParameterSnapshot, &out.ParameterSnapshot
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
	in.Timestamp.DeepCopyInto(&out.Timestamp)
}

func (in *CloudServiceHistoryEntry) DeepCopy() *CloudServiceHistoryEntry {
	if in == nil {
		return nil
	}
	out := new(CloudServiceHistoryEntry)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — CloudComponentSpec
func (in *CloudComponentSpec) DeepCopyInto(out *CloudComponentSpec) {
	*out = *in
	if in.Values != nil {
		in, out := &in.Values, &out.Values
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
	if in.ValuesOverlay != nil {
		in, out := &in.ValuesOverlay, &out.ValuesOverlay
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
	if in.DeploymentStrategy != nil {
		in, out := &in.DeploymentStrategy, &out.DeploymentStrategy
		*out = new(DeploymentStrategy)
		**out = **in
	}
	if in.Dependencies != nil {
		in, out := &in.Dependencies, &out.Dependencies
		*out = make([]Dependency, len(*in))
		copy(*out, *in)
	}
	if in.HealthCheck != nil {
		in, out := &in.HealthCheck, &out.HealthCheck
		*out = new(HealthCheck)
		**out = **in
	}
	if in.ClusterAffinity != nil {
		in, out := &in.ClusterAffinity, &out.ClusterAffinity
		*out = new(ClusterAffinity)
		**out = **in
	}
	if in.RefResources != nil {
		in, out := &in.RefResources, &out.RefResources
		*out = make([]RefResource, len(*in))
		copy(*out, *in)
	}
	if in.PersistentVolumeConfigs != nil {
		in, out := &in.PersistentVolumeConfigs, &out.PersistentVolumeConfigs
		*out = make([]PersistentVolumeConfig, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

func (in *CloudComponentSpec) DeepCopy() *CloudComponentSpec {
	if in == nil {
		return nil
	}
	out := new(CloudComponentSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — CloudComponentStatus
func (in *CloudComponentStatus) DeepCopyInto(out *CloudComponentStatus) {
	*out = *in
	if in.WorkloadStatus != nil {
		in, out := &in.WorkloadStatus, &out.WorkloadStatus
		*out = new(WorkloadStatus)
		(*in).DeepCopyInto(*out)
	}
	if in.PodsDetail != nil {
		in, out := &in.PodsDetail, &out.PodsDetail
		*out = make([]PodDetail, len(*in))
		copy(*out, *in)
	}
	if in.WorkloadDiffs != nil {
		in, out := &in.WorkloadDiffs, &out.WorkloadDiffs
		*out = make([]WorkloadDiff, len(*in))
		copy(*out, *in)
	}
	if in.LastRetryTime != nil {
		in, out := &in.LastRetryTime, &out.LastRetryTime
		*out = (*in).DeepCopy()
	}
	if in.Conditions != nil {
		in, out := &in.Conditions, &out.Conditions
		*out = make([]Condition, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.HealthCheckResult != nil {
		in, out := &in.HealthCheckResult, &out.HealthCheckResult
		*out = new(HealthCheckResult)
		(*in).DeepCopyInto(*out)
	}
	if in.History != nil {
		in, out := &in.History, &out.History
		*out = make([]ComponentHistoryEntry, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

func (in *CloudComponentStatus) DeepCopy() *CloudComponentStatus {
	if in == nil {
		return nil
	}
	out := new(CloudComponentStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — WorkloadStatus
func (in *WorkloadStatus) DeepCopyInto(out *WorkloadStatus) {
	*out = *in
	if in.Workloads != nil {
		in, out := &in.Workloads, &out.Workloads
		*out = make([]WorkloadInfo, len(*in))
		copy(*out, *in)
	}
}

func (in *WorkloadStatus) DeepCopy() *WorkloadStatus {
	if in == nil {
		return nil
	}
	out := new(WorkloadStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — HealthCheckResult
func (in *HealthCheckResult) DeepCopyInto(out *HealthCheckResult) {
	*out = *in
	if in.LastCheckTime != nil {
		in, out := &in.LastCheckTime, &out.LastCheckTime
		*out = (*in).DeepCopy()
	}
}

func (in *HealthCheckResult) DeepCopy() *HealthCheckResult {
	if in == nil {
		return nil
	}
	out := new(HealthCheckResult)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — ComponentHistoryEntry
func (in *ComponentHistoryEntry) DeepCopyInto(out *ComponentHistoryEntry) {
	*out = *in
	if in.StartTime != nil {
		in, out := &in.StartTime, &out.StartTime
		*out = (*in).DeepCopy()
	}
	if in.CompletionTime != nil {
		in, out := &in.CompletionTime, &out.CompletionTime
		*out = (*in).DeepCopy()
	}
}

func (in *ComponentHistoryEntry) DeepCopy() *ComponentHistoryEntry {
	if in == nil {
		return nil
	}
	out := new(ComponentHistoryEntry)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto — HistoryEntry
func (in *HistoryEntry) DeepCopyInto(out *HistoryEntry) {
	*out = *in
	if in.ParameterSnapshot != nil {
		in, out := &in.ParameterSnapshot, &out.ParameterSnapshot
		*out = make(map[string]interface{}, len(*in))
		for k, v := range *in {
			(*out)[k] = v
		}
	}
	in.Timestamp.DeepCopyInto(&out.Timestamp)
}

func (in *HistoryEntry) DeepCopy() *HistoryEntry {
	if in == nil {
		return nil
	}
	out := new(HistoryEntry)
	in.DeepCopyInto(out)
	return out
}

// =============================================================================
// v0.3 新增类型（ProductTask 与 Pod 明细 / 引用 / 持久化）
// =============================================================================

// DeepCopyInto 是 PersistentVolumeConfig 的深拷贝。
func (in *PersistentVolumeConfig) DeepCopyInto(out *PersistentVolumeConfig) {
	*out = *in
	if in.AccessModes != nil {
		in, out := &in.AccessModes, &out.AccessModes
		*out = make([]string, len(*in))
		copy(*out, *in)
	}
}

// DeepCopy 是 PersistentVolumeConfig 的深拷贝。
func (in *PersistentVolumeConfig) DeepCopy() *PersistentVolumeConfig {
	if in == nil {
		return nil
	}
	out := new(PersistentVolumeConfig)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto 是 TaskOrder 的深拷贝。
func (in *TaskOrder) DeepCopyInto(out *TaskOrder) {
	*out = *in
	if in.Before != nil {
		in, out := &in.Before, &out.Before
		*out = make([]string, len(*in))
		copy(*out, *in)
	}
	if in.After != nil {
		in, out := &in.After, &out.After
		*out = make([]string, len(*in))
		copy(*out, *in)
	}
}

// DeepCopy 是 TaskOrder 的深拷贝。
func (in *TaskOrder) DeepCopy() *TaskOrder {
	if in == nil {
		return nil
	}
	out := new(TaskOrder)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto 是 TaskMessage 的深拷贝。
func (in *TaskMessage) DeepCopyInto(out *TaskMessage) {
	*out = *in
	in.LastTransitionTime.DeepCopyInto(&out.LastTransitionTime)
}

// DeepCopy 是 TaskMessage 的深拷贝。
func (in *TaskMessage) DeepCopy() *TaskMessage {
	if in == nil {
		return nil
	}
	out := new(TaskMessage)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto 是 ProductTaskSpec 的深拷贝。
func (in *ProductTaskSpec) DeepCopyInto(out *ProductTaskSpec) {
	*out = *in
	if in.Resource != nil {
		in, out := &in.Resource, &out.Resource
		*out = new(TaskResource)
		**out = **in
	}
	if in.RefComponent != nil {
		in, out := &in.RefComponent, &out.RefComponent
		*out = new(TaskComponentRef)
		**out = **in
	}
	if in.TaskOrder != nil {
		in, out := &in.TaskOrder, &out.TaskOrder
		*out = new(TaskOrder)
		(*in).DeepCopyInto(*out)
	}
}

// DeepCopy 是 ProductTaskSpec 的深拷贝。
func (in *ProductTaskSpec) DeepCopy() *ProductTaskSpec {
	if in == nil {
		return nil
	}
	out := new(ProductTaskSpec)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto 是 ProductTaskStatus 的深拷贝。
func (in *ProductTaskStatus) DeepCopyInto(out *ProductTaskStatus) {
	*out = *in
	if in.Messages != nil {
		in, out := &in.Messages, &out.Messages
		*out = make([]TaskMessage, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
	if in.Conditions != nil {
		in, out := &in.Conditions, &out.Conditions
		*out = make([]Condition, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

// DeepCopy 是 ProductTaskStatus 的深拷贝。
func (in *ProductTaskStatus) DeepCopy() *ProductTaskStatus {
	if in == nil {
		return nil
	}
	out := new(ProductTaskStatus)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyInto 是 ProductTask 的深拷贝。
func (in *ProductTask) DeepCopyInto(out *ProductTask) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ObjectMeta.DeepCopyInto(&out.ObjectMeta)
	in.Spec.DeepCopyInto(&out.Spec)
	in.Status.DeepCopyInto(&out.Status)
}

// DeepCopy 是 ProductTask 的深拷贝。
func (in *ProductTask) DeepCopy() *ProductTask {
	if in == nil {
		return nil
	}
	out := new(ProductTask)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 返回 ProductTask 的深拷贝。
func (in *ProductTask) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

// DeepCopyInto 是 ProductTaskList 的深拷贝。
func (in *ProductTaskList) DeepCopyInto(out *ProductTaskList) {
	*out = *in
	out.TypeMeta = in.TypeMeta
	in.ListMeta.DeepCopyInto(&out.ListMeta)
	if in.Items != nil {
		in, out := &in.Items, &out.Items
		*out = make([]ProductTask, len(*in))
		for i := range *in {
			(*in)[i].DeepCopyInto(&(*out)[i])
		}
	}
}

// DeepCopy 是 ProductTaskList 的深拷贝。
func (in *ProductTaskList) DeepCopy() *ProductTaskList {
	if in == nil {
		return nil
	}
	out := new(ProductTaskList)
	in.DeepCopyInto(out)
	return out
}

// DeepCopyObject 返回 ProductTaskList 的深拷贝。
func (in *ProductTaskList) DeepCopyObject() runtime.Object {
	return in.DeepCopy()
}

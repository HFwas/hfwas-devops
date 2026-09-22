package service

import (
	"context"
	"fmt"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/client-go/dynamic"
	"k8s.io/client-go/kubernetes"
	clientgoscheme "k8s.io/client-go/kubernetes/scheme"
	_ "k8s.io/client-go/plugin/pkg/client/auth"
	"k8s.io/client-go/tools/clientcmd"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"strings"
)

// KubeClient 封装 K8s API 操作
type KubeClient struct {
	clientset     *kubernetes.Clientset
	client        client.Client
	dynamicClient dynamic.Interface
}

func NewKubeClient(kubeconfig []byte) (*KubeClient, error) {
	restConfig, err := clientcmd.RESTConfigFromKubeConfig(kubeconfig)
	if err != nil {
		return nil, fmt.Errorf("parse kubeconfig: %w", err)
	}

	clientset, err := kubernetes.NewForConfig(restConfig)
	if err != nil {
		return nil, fmt.Errorf("create clientset: %w", err)
	}

	// controller-runtime 客户端用于 CRD 操作
	scheme := clientgoscheme.Scheme
	// 注册 delivery.hfwas.io CRD
	// 实际场景需要 import cn-app-operator API
	ctrlClient, err := client.New(restConfig, client.Options{Scheme: scheme})
	if err != nil {
		// CRD 客户端非必须，降级可用（dynamic client 仍可用）
	}

	dynamicClient, err := dynamic.NewForConfig(restConfig)
	if err != nil {
		return nil, fmt.Errorf("create dynamic client: %w", err)
	}

	return &KubeClient{clientset: clientset, client: ctrlClient, dynamicClient: dynamicClient}, nil
}

func (k *KubeClient) Ping() (string, error) {
	v, err := k.clientset.DiscoveryClient.ServerVersion()
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%s.%s", v.Major, v.Minor), nil
}

func (k *KubeClient) GetNodes() ([]NodeInfo, error) {
	nodes, err := k.clientset.CoreV1().Nodes().List(context.Background(), emptyListOptions())
	if err != nil {
		return nil, err
	}
	var result []NodeInfo
	for _, n := range nodes.Items {
		ready := false
		for _, cond := range n.Status.Conditions {
			if cond.Type == "Ready" {
				ready = cond.Status == "True"
			}
		}
		result = append(result, NodeInfo{
			Name:              n.Name,
			Ready:             ready,
			KubeletVersion:    n.Status.NodeInfo.KubeletVersion,
			OSImage:           n.Status.NodeInfo.OSImage,
			AllocatableCPU:    n.Status.Allocatable.Cpu().String(),
			AllocatableMemory: n.Status.Allocatable.Memory().String(),
		})
	}
	return result, nil
}

func (k *KubeClient) GetStorageClasses() ([]SCInfo, error) {
	scs, err := k.clientset.StorageV1().StorageClasses().List(context.Background(), emptyListOptions())
	if err != nil {
		return nil, err
	}
	var result []SCInfo
	for _, sc := range scs.Items {
		isDefault := false
		if sc.Annotations["storageclass.kubernetes.io/is-default-class"] == "true" {
			isDefault = true
		}
		result = append(result, SCInfo{
			Name:        sc.Name,
			Provisioner: sc.Provisioner,
			IsDefault:   isDefault,
		})
	}
	return result, nil
}

func (k *KubeClient) GetNamespaces() ([]NSInfo, error) {
	nsList, err := k.clientset.CoreV1().Namespaces().List(context.Background(), emptyListOptions())
	if err != nil {
		return nil, err
	}
	var result []NSInfo
	for _, ns := range nsList.Items {
		result = append(result, NSInfo{Name: ns.Name, Phase: string(ns.Status.Phase)})
	}
	return result, nil
}

func (k *KubeClient) EnsureNamespace(name string, labels map[string]string) error {
	ns, err := k.clientset.CoreV1().Namespaces().Get(context.Background(), name, metav1GetOptions())
	if err == nil {
		_ = ns
		return nil
	}
	nso := &corev1.Namespace{}
	nso.Name = name
	nso.Labels = labels
	_, err = k.clientset.CoreV1().Namespaces().Create(context.Background(), nso, metav1CreateOptions())
	return err
}

func (k *KubeClient) ListPods(namespace string) ([]PodInfo, error) {
	pods, err := k.clientset.CoreV1().Pods(namespace).List(context.Background(), emptyListOptions())
	if err != nil {
		return nil, err
	}
	var result []PodInfo
	for _, p := range pods.Items {
		phase := string(p.Status.Phase)
		ready := false
		for _, c := range p.Status.Conditions {
			if c.Type == "Ready" {
				ready = c.Status == "True"
			}
		}
		result = append(result, PodInfo{
			Name:     p.Name,
			Status:   phase,
			Ready:    ready,
			Restarts: getTotalRestarts(p),
			NodeName: p.Spec.NodeName,
		})
	}
	return result, nil
}

func (k *KubeClient) ListServices(namespace string) ([]SvcInfo, error) {
	svcs, err := k.clientset.CoreV1().Services(namespace).List(context.Background(), emptyListOptions())
	if err != nil {
		return nil, err
	}
	var result []SvcInfo
	for _, s := range svcs.Items {
		result = append(result, SvcInfo{
			Name:      s.Name,
			Type:      string(s.Spec.Type),
			ClusterIP: s.Spec.ClusterIP,
			Ports:     formatPorts(s.Spec.Ports),
		})
	}
	return result, nil
}

func getTotalRestarts(p corev1.Pod) int {
	total := 0
	for _, cs := range p.Status.ContainerStatuses {
		total += int(cs.RestartCount)
	}
	return total
}

func formatPorts(ports []corev1.ServicePort) string {
	if len(ports) == 0 {
		return ""
	}
	parts := make([]string, len(ports))
	for i, p := range ports {
		parts[i] = fmt.Sprintf("%d/%s", p.Port, p.Protocol)
	}
	return strings.Join(parts, ", ")
}

type PodInfo struct {
	Name     string `json:"name"`
	Status   string `json:"status"`
	Ready    bool   `json:"ready"`
	Restarts int    `json:"restarts"`
	NodeName string `json:"nodeName"`
}

type SvcInfo struct {
	Name      string `json:"name"`
	Type      string `json:"type"`
	ClusterIP string `json:"clusterIP"`
	Ports     string `json:"ports"`
}

func (k *KubeClient) GetPVCs(namespace string) ([]PVCInfo, error) {
	opts := emptyListOptions()
	var ns string
	if namespace != "" {
		ns = namespace
	}
	pvcs, err := k.clientset.CoreV1().PersistentVolumeClaims(ns).List(context.Background(), opts)
	if err != nil {
		return nil, err
	}
	var result []PVCInfo
	for _, pvc := range pvcs.Items {
		sc := ""
		if pvc.Spec.StorageClassName != nil {
			sc = *pvc.Spec.StorageClassName
		}
		result = append(result, PVCInfo{
			Name:         pvc.Name,
			Namespace:    pvc.Namespace,
			Phase:        string(pvc.Status.Phase),
			StorageClass: sc,
			Capacity:     pvc.Status.Capacity.Storage().String(),
			AccessModes:  toStringSlice(pvc.Spec.AccessModes),
		})
	}
	return result, nil
}

// CRD 操作（cn-app-operator）
func (k *KubeClient) CreateAppCR(namespace, name, displayName, version, csName string, params map[string]interface{}, globalParams map[string]interface{}) error {
	// 使用 unstructured API 创建 App CR
	// apiVersion: delivery.hfwas.io/v1
	// kind: App
	app := &unstructured.Unstructured{}
	app.SetAPIVersion("delivery.hfwas.io/v1")
	app.SetKind("App")
	app.SetName(name)
	app.SetNamespace(namespace)
	app.SetLabels(map[string]string{
		"delivery.hfwas.io/product":    name,
		"delivery.hfwas.io/managed-by": "delivery-platform",
	})

	if err := unstructured.SetNestedField(app.Object, displayName, "spec", "displayName"); err != nil {
		return err
	}
	unstructured.SetNestedField(app.Object, version, "spec", "version")

	// globalParameters
	if globalParams != nil {
		for k, v := range globalParams {
			unstructured.SetNestedField(app.Object, toString(v), "spec", "globalParameters", k)
		}
	}

	// services
	services := []interface{}{
		map[string]interface{}{
			"name":       csName,
			"alias":      csName,
			"parameters": params,
		},
	}
	unstructured.SetNestedSlice(app.Object, services, "spec", "services")

	// 尝试先更新，不存在则创建
	existing, err := k.dynamicClient.Resource(appGVR).Namespace(namespace).Get(context.Background(), name, metav1.GetOptions{})
	if err == nil && existing != nil {
		existing.Object["spec"] = app.Object["spec"]
		_, err = k.dynamicClient.Resource(appGVR).Namespace(namespace).Update(context.Background(), existing, metav1.UpdateOptions{})
		return err
	}

	_, err = k.dynamicClient.Resource(appGVR).Namespace(namespace).Create(context.Background(), app, metav1.CreateOptions{})
	return err
}

func (k *KubeClient) CreateCloudServiceCR(namespace, name, displayName, version string, components []interface{}, parameters []interface{}, images []interface{}) error {
	cs := &unstructured.Unstructured{}
	cs.SetAPIVersion("delivery.hfwas.io/v1")
	cs.SetKind("CloudService")
	cs.SetName(name)
	cs.SetNamespace(namespace)
	cs.SetLabels(map[string]string{
		"delivery.hfwas.io/managed-by": "delivery-platform",
	})

	unstructured.SetNestedField(cs.Object, displayName, "spec", "displayName")
	unstructured.SetNestedField(cs.Object, version, "spec", "version")
	if components != nil {
		unstructured.SetNestedSlice(cs.Object, components, "spec", "components")
	}
	if parameters != nil {
		unstructured.SetNestedSlice(cs.Object, parameters, "spec", "parameters")
	}
	if images != nil {
		unstructured.SetNestedSlice(cs.Object, images, "spec", "imageList")
	}

	existing, err := k.dynamicClient.Resource(csGVR).Namespace(namespace).Get(context.Background(), name, metav1.GetOptions{})
	if err == nil && existing != nil {
		existing.Object["spec"] = cs.Object["spec"]
		_, err = k.dynamicClient.Resource(csGVR).Namespace(namespace).Update(context.Background(), existing, metav1.UpdateOptions{})
		return err
	}

	_, err = k.dynamicClient.Resource(csGVR).Namespace(namespace).Create(context.Background(), cs, metav1.CreateOptions{})
	return err
}

func (k *KubeClient) DeleteAppCR(namespace, name string) error {
	return k.dynamicClient.Resource(appGVR).Namespace(namespace).Delete(context.Background(), name, metav1.DeleteOptions{})
}

var (
	appGVR = schema.GroupVersionResource{Group: "delivery.hfwas.io", Version: "v1", Resource: "apps"}
	csGVR  = schema.GroupVersionResource{Group: "delivery.hfwas.io", Version: "v1", Resource: "cloudservices"}
)

func toString(v interface{}) string {
	if s, ok := v.(string); ok {
		return s
	}
	return fmt.Sprintf("%v", v)
}

func toStringSlice(s []corev1.PersistentVolumeAccessMode) []string {
	result := make([]string, len(s))
	for i, v := range s {
		result[i] = string(v)
	}
	return result
}

// 类型
type NodeInfo struct {
	Name              string `json:"name"`
	Ready             bool   `json:"ready"`
	KubeletVersion    string `json:"kubeletVersion"`
	OSImage           string `json:"osImage"`
	AllocatableCPU    string `json:"allocatableCpu"`
	AllocatableMemory string `json:"allocatableMemory"`
}

type SCInfo struct {
	Name        string `json:"name"`
	Provisioner string `json:"provisioner"`
	IsDefault   bool   `json:"isDefault"`
}

type NSInfo struct {
	Name  string `json:"name"`
	Phase string `json:"phase"`
}

type PVCInfo struct {
	Name         string   `json:"name"`
	Namespace    string   `json:"namespace"`
	Phase        string   `json:"phase"`
	StorageClass string   `json:"storageClass"`
	Capacity     string   `json:"capacity"`
	AccessModes  []string `json:"accessModes"`
}

func emptyListOptions() metav1.ListOptions {
	return metav1.ListOptions{}
}

func metav1GetOptions() metav1.GetOptions {
	return metav1.GetOptions{}
}

func metav1CreateOptions() metav1.CreateOptions {
	return metav1.CreateOptions{}
}

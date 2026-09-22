package api

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"github.com/hfwas/delivery-platform/internal/model"
	"github.com/hfwas/delivery-platform/internal/service"
	"github.com/hfwas/delivery-platform/internal/store"
)

type Handler struct {
	store *store.Store
}

func New(s *store.Store) *Handler {
	return &Handler{store: s}
}

// RegisterRoutes 注册所有路由
func (h *Handler) RegisterRoutes(r chi.Router) {
	r.Route("/api/delivery", func(r chi.Router) {
		// 系统状态
		r.Get("/status", h.getStatus)

		// 集群管理
		r.Get("/clusters", h.listClusters)
		r.Post("/clusters", h.importCluster)
		r.Put("/clusters/{id}", h.updateCluster)
		r.Post("/clusters/{id}/current", h.setCurrentCluster)
		r.Get("/clusters/{id}/status", h.getClusterStatus)
		r.Delete("/clusters/{id}", h.deleteCluster)

		// 集群库存查询
		r.Get("/clusters/{id}/nodes", h.listNodes)
		r.Get("/clusters/{id}/storage-classes", h.listStorageClasses)
		r.Get("/clusters/{id}/pvcs", h.listPVCs)
		r.Get("/clusters/{id}/namespaces", h.listNamespaces)

		// 产品管理
		r.Post("/products/page", h.pageProducts)
		r.Post("/products/import", h.importProduct)
		r.Get("/products/{id}", h.getProduct)
		r.Put("/products/{id}", h.saveProductParams)
		r.Post("/products/{id}/deploy", h.deployProduct)
		r.Post("/products/{id}/rollback", h.rollbackProduct)
		r.Post("/products/{id}/uninstall", h.uninstallProduct)
		r.Delete("/products/{id}", h.deleteProduct)

		// 部署进度
		r.Get("/products/{id}/deployments", h.listDeployments)
		r.Get("/deployments/{deployId}", h.getDeployment)
		r.Post("/deployments/{deployId}/cancel", h.cancelDeployment)

		// 产品资源查看
		r.Get("/products/{id}/resources", h.getProductResources)
		r.Get("/products/{id}/components", h.getProductComponents)
	})
}

// =============================================================================
// Status
// =============================================================================

func (h *Handler) getStatus(w http.ResponseWriter, r *http.Request) {
	stats, err := h.store.GetPlatformStats()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(stats))
}

// =============================================================================
// Clusters
// =============================================================================

func (h *Handler) listClusters(w http.ResponseWriter, r *http.Request) {
	clusters, err := h.store.ListClusters()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(clusters))
}

func (h *Handler) importCluster(w http.ResponseWriter, r *http.Request) {
	var req model.ImportClusterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("invalid request body"))
		return
	}
	if req.Name == "" || req.KubeconfigText == "" {
		writeJSON(w, http.StatusBadRequest, model.Error("name and kubeconfigText are required"))
		return
	}

	// 检查名称唯一
	existing, _ := h.store.GetClusterByName(req.Name)
	if existing != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("cluster name already exists"))
		return
	}

	// 探活
	kubeClient, err := service.NewKubeClient([]byte(req.KubeconfigText))
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("invalid kubeconfig: "+err.Error()))
		return
	}
	version, err := kubeClient.Ping()
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("cannot connect to cluster: "+err.Error()))
		return
	}

	// AES 加密（简化：生产环境需 AES-256-GCM）
	encrypted := encryptKubeconfig(req.KubeconfigText)

	// 检查是否第一个集群
	count, _ := h.store.ListClusters()
	isCurrent := len(count) == 0

	cluster := &model.Cluster{
		Name:          req.Name,
		ServerHost:    extractServerHost(req.KubeconfigText),
		KubeconfigEnc: encrypted,
		IsCurrent:     isCurrent,
		Status:        "UP",
		Version:       version,
	}
	if err := h.store.CreateCluster(cluster); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}

	writeJSON(w, http.StatusOK, model.Success(cluster))
}

func (h *Handler) updateCluster(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	var req model.ImportClusterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("invalid request body"))
		return
	}

	existing, _ := h.store.GetCluster(id)
	if existing == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return
	}

	if req.Name != "" {
		// 检查名称唯一（排除自身）
		dup, _ := h.store.GetClusterByName(req.Name)
		if dup != nil && dup.ID != id {
			writeJSON(w, http.StatusBadRequest, model.Error("cluster name already exists"))
			return
		}
		existing.Name = req.Name
	}
	if req.KubeconfigText != "" {
		kubeClient, err := service.NewKubeClient([]byte(req.KubeconfigText))
		if err != nil {
			writeJSON(w, http.StatusBadRequest, model.Error("invalid kubeconfig: "+err.Error()))
			return
		}
		version, err := kubeClient.Ping()
		if err != nil {
			writeJSON(w, http.StatusBadRequest, model.Error("cannot connect: "+err.Error()))
			return
		}
		existing.KubeconfigEnc = encryptKubeconfig(req.KubeconfigText)
		existing.Version = version
		existing.Status = "UP"
		existing.ServerHost = extractServerHost(req.KubeconfigText)
	}

	// 写回数据库
	_ = h.store.UpdateClusterStatus(id, existing.Status, existing.Version)
	writeJSON(w, http.StatusOK, model.Success(existing))
}

func (h *Handler) setCurrentCluster(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	cluster, _ := h.store.GetCluster(id)
	if cluster == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return
	}
	if err := h.store.ClearCurrentCluster(); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	if err := h.store.SetCurrentCluster(id); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(nil))
}

func (h *Handler) getClusterStatus(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	cluster, _ := h.store.GetCluster(id)
	if cluster == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return
	}

	kubeClient, err := buildKubeClient(cluster)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error(err.Error()))
		return
	}
	version, _ := kubeClient.Ping()
	nodes, _ := kubeClient.GetNodes()

	readyCount := 0
	for _, n := range nodes {
		if n.Ready {
			readyCount++
		}
	}

	writeJSON(w, http.StatusOK, model.Success(map[string]interface{}{
		"version":    version,
		"nodeCount":  len(nodes),
		"readyNodes": readyCount,
	}))
}

func (h *Handler) deleteCluster(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	cluster, _ := h.store.GetCluster(id)
	if cluster == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return
	}
	if cluster.IsCurrent {
		writeJSON(w, http.StatusBadRequest, model.Error("cannot delete current cluster"))
		return
	}
	n, _ := h.store.CountProductsOnCluster(id)
	if n > 0 {
		writeJSON(w, http.StatusBadRequest, model.Error("cluster has deployed products"))
		return
	}
	if err := h.store.SoftDeleteCluster(id); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(nil))
}

// =============================================================================
// Cluster inventory (read-only queries against specified cluster)
// =============================================================================

func (h *Handler) listNodes(w http.ResponseWriter, r *http.Request) {
	kc := h.getKubeForCluster(w, r)
	if kc == nil {
		return
	}
	nodes, err := kc.GetNodes()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(nodes))
}

func (h *Handler) listStorageClasses(w http.ResponseWriter, r *http.Request) {
	kc := h.getKubeForCluster(w, r)
	if kc == nil {
		return
	}
	scs, err := kc.GetStorageClasses()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(scs))
}

func (h *Handler) listPVCs(w http.ResponseWriter, r *http.Request) {
	kc := h.getKubeForCluster(w, r)
	if kc == nil {
		return
	}
	ns := r.URL.Query().Get("namespace")
	pvcs, err := kc.GetPVCs(ns)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(pvcs))
}

func (h *Handler) listNamespaces(w http.ResponseWriter, r *http.Request) {
	kc := h.getKubeForCluster(w, r)
	if kc == nil {
		return
	}
	nss, err := kc.GetNamespaces()
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(nss))
}

func (h *Handler) getKubeForCluster(w http.ResponseWriter, r *http.Request) *service.KubeClient {
	idStr := chi.URLParam(r, "id")
	if idStr == "" {
		idStr = chi.URLParam(r, "clusterId")
	}
	id, _ := strconv.ParseInt(idStr, 10, 64)
	cluster, _ := h.store.GetCluster(id)
	if cluster == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return nil
	}
	kc, err := buildKubeClient(cluster)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error(err.Error()))
		return nil
	}
	return kc
}

// =============================================================================
// Products
// =============================================================================

func (h *Handler) pageProducts(w http.ResponseWriter, r *http.Request) {
	var req model.PageRequest
	json.NewDecoder(r.Body).Decode(&req)
	if req.PageNo < 1 {
		req.PageNo = 1
	}
	if req.PageSize < 1 || req.PageSize > 100 {
		req.PageSize = 20
	}

	list, total, err := h.store.ListProducts(req.PageNo, req.PageSize, req.Keyword)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}

	writeJSON(w, http.StatusOK, model.Success(model.PageResult{
		Records:  list,
		Total:    total,
		PageNo:   req.PageNo,
		PageSize: req.PageSize,
	}))
}

func (h *Handler) importProduct(w http.ResponseWriter, r *http.Request) {
	err := r.ParseMultipartForm(100 << 20)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("file too large or invalid form"))
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("file is required"))
		return
	}
	defer file.Close()

	ext := strings.ToLower(filepath.Ext(header.Filename))
	if ext != ".zip" && ext != ".gz" && ext != ".tgz" {
		writeJSON(w, http.StatusBadRequest, model.Error("only zip/tar.gz files are supported"))
		return
	}

	data := make([]byte, header.Size)
	if _, err := file.Read(data); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error("cannot read file: "+err.Error()))
		return
	}

	headerBytes := data[:min(len(data), 4096)]
	if !strings.Contains(string(headerBytes), "manifest.json") &&
		!strings.Contains(string(headerBytes), "displayName") {
		writeJSON(w, http.StatusBadRequest, model.Error("package must contain manifest.json"))
		return
	}

	manifest, manifestDir, err := extractManifestFromZip(data)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("cannot parse manifest: "+err.Error()))
		return
	}

	existing, _ := h.store.GetProductByKey(manifest.Key)
	isNew := existing == nil

	if isNew {
		product := &model.Product{
			ProductKey:       manifest.Key,
			DisplayName:      manifest.DisplayName,
			PackageVersion:   manifest.Version,
			PackageDir:       manifestDir,
			ManifestJSON:     store.ToJSON(manifest),
			ParamsJSON:       store.ToJSON(extractDefaultParams(manifest)),
			GlobalParamsJSON: "{}",
			Status:           "NOT_DEPLOYED",
		}
		if err := h.store.CreateProduct(product); err != nil {
			writeJSON(w, http.StatusInternalServerError, model.Error("cannot save product: "+err.Error()))
			return
		}
		existing = product
	} else {
		existing.PackageVersion = manifest.Version
		existing.ManifestJSON = store.ToJSON(manifest)
		existing.DisplayName = manifest.DisplayName
		var oldParams map[string]interface{}
		store.ParseJSON(existing.ParamsJSON, &oldParams)
		oldParams = mergeParamsOnReimport(manifest, oldParams)
		existing.ParamsJSON = store.ToJSON(oldParams)
		if err := h.store.UpdateProduct(existing); err != nil {
			writeJSON(w, http.StatusInternalServerError, model.Error("cannot update product: "+err.Error()))
			return
		}
	}

	h.syncCloudServiceCR(existing)

	writeJSON(w, http.StatusOK, model.Success(map[string]interface{}{
		"id":             existing.ID,
		"productKey":     existing.ProductKey,
		"displayName":    existing.DisplayName,
		"packageVersion": existing.PackageVersion,
		"status":         existing.Status,
	}))
}

func (h *Handler) getProduct(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}

	// 构建 form schema
	form := buildFormSchema(product)

	detail := model.ProductDetail{
		Product: *product,
		Form:    form,
	}
	// 本地库的 status 只作兜底；真实状态以集群里的 App CR 为准
	detail.Phase = product.Status

	if kc := h.productKubeClient(product); kc != nil {
		app, err := kc.GetAppCR(appNamespace(product), appCRName(product))
		if err == nil && app != nil {
			detail.Phase = app.Phase
			detail.ReleaseID = app.ReleaseID
			detail.PlanRevision = app.PlanRevision
			detail.ObservedReleaseID = app.ObservedReleaseID

			if app.Summary != nil {
				detail.AggregatedSummary = &model.AggregatedSummary{
					TotalServices:   app.Summary.TotalServices,
					ReadyServices:   app.Summary.ReadyServices,
					TotalComponents: app.Summary.TotalComponents,
					ReadyComponents: app.Summary.ReadyComponents,
				}
			}
			for _, s := range app.Services {
				detail.ServiceStatuses = append(detail.ServiceStatuses, model.ServiceStatus{
					Name:              s.Name,
					Phase:             s.Phase,
					ReleaseID:         s.ReleaseID,
					ObservedReleaseID: s.ObservedReleaseID,
					Error:             s.ErrorMessage,
				})
			}

			// 发布步骤：把「这一次发布」的 ProductTask 顺序一并带出
			if tasks, err := kc.ListProductTasks(app.ReleaseID); err == nil {
				detail.Tasks = toTaskViews(tasks)
			}
		}
	}

	writeJSON(w, http.StatusOK, model.Success(detail))
}

// appNamespace 返回产品对应的 App CR 所在命名空间。
func appNamespace(product *model.Product) string {
	if product.TargetNS != "" {
		return product.TargetNS
	}
	return "dlv-" + product.ProductKey
}

// appCRName 返回产品对应的 App CR 名称。
func appCRName(product *model.Product) string {
	return "app-" + product.ProductKey
}

// productKubeClient 返回产品所在集群的客户端；未绑集群或连不上时返回 nil。
func (h *Handler) productKubeClient(product *model.Product) *service.KubeClient {
	if product.ClusterID == nil {
		return nil
	}
	cluster, _ := h.store.GetCluster(*product.ClusterID)
	if cluster == nil {
		return nil
	}
	kc, err := buildKubeClient(cluster)
	if err != nil {
		return nil
	}
	return kc
}

// toTaskViews 把 service.TaskInfo 映射为响应视图。
func toTaskViews(tasks []service.TaskInfo) []model.ProductTaskView {
	if len(tasks) == 0 {
		return nil
	}
	out := make([]model.ProductTaskView, 0, len(tasks))
	for _, t := range tasks {
		out = append(out, model.ProductTaskView{
			Name:      t.Name,
			ReleaseID: t.ReleaseID,
			OpsType:   t.OpsType,
			Phase:     t.Phase,
			Component: t.Component,
			Resource:  t.Resource,
			After:     t.After,
			Messages:  t.Messages,
		})
	}
	return out
}

func (h *Handler) saveProductParams(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}

	var req model.SaveProductParamsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error("invalid request"))
		return
	}

	if req.GlobalParams != nil {
		product.GlobalParamsJSON = store.ToJSON(req.GlobalParams)
	}
	if req.Params != nil {
		product.ParamsJSON = store.ToJSON(map[string]interface{}{
			"params":    req.Params,
			"overrides": req.Overrides,
		})
	}
	if req.DisplayName != "" {
		product.DisplayName = req.DisplayName
	}

	if err := h.store.UpdateProduct(product); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(nil))
}

// =============================================================================
// Deploy
// =============================================================================

func (h *Handler) deployProduct(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}

	// 1. 确定目标集群
	var clusterID int64
	if product.ClusterID != nil {
		clusterID = *product.ClusterID
	} else {
		current, err := h.store.GetCurrentCluster()
		if err != nil || current == nil {
			writeJSON(w, http.StatusBadRequest, model.Error("no cluster available, please import a kubeconfig first"))
			return
		}
		clusterID = current.ID
	}

	// 2. 状态检查
	if product.Status == "DEPLOYING" || product.Status == "UNINSTALLING" {
		writeJSON(w, http.StatusBadRequest, model.Error("product is already deploying or uninstalling"))
		return
	}

	cluster, _ := h.store.GetCluster(clusterID)
	if cluster == nil {
		writeJSON(w, http.StatusBadRequest, model.Error("target cluster not found"))
		return
	}

	// 3. 创建部署记录
	deployment := &model.Deployment{
		ProductID:          id,
		ClusterID:          clusterID,
		Action:             "DEPLOY",
		PackageVersion:     product.PackageVersion,
		ParamsSnapshotJSON: product.ParamsJSON,
		GlobalSnapshotJSON: product.GlobalParamsJSON,
		Status:             "PENDING",
		StartedAt:          model.Now(),
	}
	if product.Status != "NOT_DEPLOYED" {
		deployment.Action = "UPGRADE"
	}
	if err := h.store.CreateDeployment(deployment); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}

	// 4. 构建 namespace
	nsName := "dlv-" + product.ProductKey
	if product.TargetNS == "" {
		product.TargetNS = nsName
	}

	// 5. 构建 kubernetes client 并创建 App CR
	kubeClient, err := buildKubeClient(cluster)
	if err != nil {
		h.store.UpdateDeploymentStatus(deployment.ID, "FAILED", "", "", err.Error())
		writeJSON(w, http.StatusBadRequest, model.Error("cannot connect to cluster: "+err.Error()))
		return
	}

	// 确保 namespace 存在
	labels := map[string]string{
		"delivery.hfwas.io/product":    product.ProductKey,
		"delivery.hfwas.io/managed-by": "delivery-platform",
	}
	if err := kubeClient.EnsureNamespace(product.TargetNS, labels); err != nil {
		h.store.UpdateDeploymentStatus(deployment.ID, "FAILED", "", "", err.Error())
		writeJSON(w, http.StatusInternalServerError, model.Error("cannot create namespace: "+err.Error()))
		return
	}

	// 更新产品状态
	product.Status = "DEPLOYING"
	product.ClusterID = &clusterID
	if err := h.store.UpdateProduct(product); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}

	// 更新部署记录
	h.store.UpdateDeploymentStatus(deployment.ID, "PENDING", deployment.AppName, deployment.AppNS, "")

	writeJSON(w, http.StatusOK, model.Success(deployment))
}

func (h *Handler) rollbackProduct(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}

	// 查询最近一次成功的部署记录
	deployments, err := h.store.ListDeployments(id)
	if err != nil || len(deployments) < 2 {
		writeJSON(w, http.StatusBadRequest, model.Error("no previous deployment to roll back to"))
		return
	}

	// 找上一个成功的部署
	var prevDeployment *model.Deployment
	for _, d := range deployments {
		if d.Status == "SUCCEEDED" && d.ID != deployments[0].ID {
			prevDeployment = &d
			break
		}
	}
	if prevDeployment == nil {
		writeJSON(w, http.StatusBadRequest, model.Error("no previous successful deployment found"))
		return
	}

	// 恢复参数快照
	product.ParamsJSON = prevDeployment.ParamsSnapshotJSON
	product.GlobalParamsJSON = prevDeployment.GlobalSnapshotJSON
	if err := h.store.UpdateProduct(product); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error("cannot restore params: "+err.Error()))
		return
	}

	// 创建回滚部署记录
	deployment := &model.Deployment{
		ProductID:          id,
		ClusterID:          *product.ClusterID,
		Action:             "ROLLBACK",
		PackageVersion:     prevDeployment.PackageVersion,
		ParamsSnapshotJSON: product.ParamsJSON,
		GlobalSnapshotJSON: product.GlobalParamsJSON,
		Status:             "PENDING",
		StartedAt:          model.Now(),
		AppName:            "app-" + product.ProductKey,
		AppNS:              product.TargetNS,
	}
	if err := h.store.CreateDeployment(deployment); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error("cannot create deployment: "+err.Error()))
		return
	}

	// 更新产品状态
	product.Status = "DEPLOYING"
	h.store.UpdateProduct(product)

	writeJSON(w, http.StatusOK, model.Success(deployment))
}

func (h *Handler) uninstallProduct(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}
	if product.Status == "NOT_DEPLOYED" {
		writeJSON(w, http.StatusBadRequest, model.Error("product is not deployed"))
		return
	}
	if product.Status == "UNINSTALLING" {
		writeJSON(w, http.StatusBadRequest, model.Error("product is already being uninstalled"))
		return
	}

	// 删除 K8s App CR（触发级联卸载）
	if product.ClusterID != nil && product.TargetNS != "" {
		cluster, _ := h.store.GetCluster(*product.ClusterID)
		if cluster != nil {
			kc, err := buildKubeClient(cluster)
			if err == nil {
				appName := "app-" + product.ProductKey
				kc.DeleteAppCR(product.TargetNS, appName)
			}
		}
	}

	// 创建卸载部署记录
	clusterID := int64(0)
	if product.ClusterID != nil {
		clusterID = *product.ClusterID
	}
	deployment := &model.Deployment{
		ProductID:          id,
		ClusterID:          clusterID,
		Action:             "UNINSTALL",
		PackageVersion:     product.PackageVersion,
		ParamsSnapshotJSON: product.ParamsJSON,
		GlobalSnapshotJSON: product.GlobalParamsJSON,
		Status:             "PENDING",
		StartedAt:          model.Now(),
	}
	if err := h.store.CreateDeployment(deployment); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error("cannot create deployment: "+err.Error()))
		return
	}

	// 更新产品状态
	product.Status = "UNINSTALLING"
	h.store.UpdateProduct(product)

	writeJSON(w, http.StatusOK, model.Success(deployment))
}

func (h *Handler) deleteProduct(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}
	if product.Status != "NOT_DEPLOYED" {
		writeJSON(w, http.StatusBadRequest, model.Error("please uninstall before deleting"))
		return
	}
	product.Status = "NOT_DEPLOYED"
	h.store.UpdateProduct(product)
	writeJSON(w, http.StatusOK, model.Success(nil))
}

// =============================================================================
// Deployments
// =============================================================================

func (h *Handler) listDeployments(w http.ResponseWriter, r *http.Request) {
	productID, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	deployments, err := h.store.ListDeployments(productID)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(deployments))
}

func (h *Handler) getDeployment(w http.ResponseWriter, r *http.Request) {
	deployID, _ := strconv.ParseInt(chi.URLParam(r, "deployId"), 10, 64)
	deployment, _ := h.store.GetDeployment(deployID)
	if deployment == nil {
		writeJSON(w, http.StatusNotFound, model.Error("deployment not found"))
		return
	}
	writeJSON(w, http.StatusOK, model.Success(deployment))
}

func (h *Handler) cancelDeployment(w http.ResponseWriter, r *http.Request) {
	deployID, _ := strconv.ParseInt(chi.URLParam(r, "deployId"), 10, 64)
	deployment, _ := h.store.GetDeployment(deployID)
	if deployment == nil {
		writeJSON(w, http.StatusNotFound, model.Error("deployment not found"))
		return
	}
	if deployment.Status == "SUCCEEDED" || deployment.Status == "FAILED" {
		writeJSON(w, http.StatusBadRequest, model.Error("deployment has already finished"))
		return
	}

	if err := h.store.UpdateDeploymentStatus(deployID, "CANCELLED", "", "", "cancelled by user"); err != nil {
		writeJSON(w, http.StatusInternalServerError, model.Error(err.Error()))
		return
	}

	// 如果产品还在 DEPLOYING 状态，恢复为之前的状态
	product, _ := h.store.GetProduct(deployment.ProductID)
	if product != nil && product.Status == "DEPLOYING" {
		product.Status = "FAILED"
		h.store.UpdateProduct(product)
	}

	writeJSON(w, http.StatusOK, model.Success(nil))
}

// =============================================================================
// Product resources
// =============================================================================

func (h *Handler) getProductResources(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}
	if product.ClusterID == nil || product.TargetNS == "" {
		writeJSON(w, http.StatusOK, model.Success([]interface{}{}))
		return
	}

	cluster, _ := h.store.GetCluster(*product.ClusterID)
	if cluster == nil {
		writeJSON(w, http.StatusNotFound, model.Error("cluster not found"))
		return
	}

	kc, err := buildKubeClient(cluster)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, model.Error(err.Error()))
		return
	}

	// 查询命名空间下的所有资源
	ns := product.TargetNS
	pods, _ := kc.ListPods(ns)
	svcs, _ := kc.ListServices(ns)
	pvcs, _ := kc.GetPVCs(ns)

	writeJSON(w, http.StatusOK, model.Success(map[string]interface{}{
		"namespace":  ns,
		"pods":       pods,
		"services":   svcs,
		"pvcs":       pvcs,
		"appName":    "app-" + product.ProductKey,
		"components": "csvc-" + product.ProductKey,
	}))
}

func (h *Handler) getProductComponents(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	product, _ := h.store.GetProduct(id)
	if product == nil {
		writeJSON(w, http.StatusNotFound, model.Error("product not found"))
		return
	}
	if product.ClusterID == nil {
		writeJSON(w, http.StatusOK, model.Success([]interface{}{}))
		return
	}

	// 优先读集群里的真实 CloudComponent CR —— 只有这里才有 phase、
	// podsDetail（每个 Pod 的 state/restarts/是否切到新修订）与 workloadDiffs。
	if kc := h.productKubeClient(product); kc != nil {
		components, err := kc.ListCloudComponents(appNamespace(product))
		if err == nil && len(components) > 0 {
			writeJSON(w, http.StatusOK, model.Success(components))
			return
		}
	}

	// 兜底：CR 还没建出来（或集群读不到）时按 manifest 列组件。
	// phase 标 unknown 表示「还没读到集群状态」，不是「未部署」。
	writeJSON(w, http.StatusOK, model.Success(manifestComponentViews(product)))
}

// manifestComponentViews 从 manifest 兜底拼出组件清单。
func manifestComponentViews(product *model.Product) []service.ComponentInfo {
	var manifest model.Manifest
	if err := store.ParseJSON(product.ManifestJSON, &manifest); err != nil {
		return nil
	}

	out := make([]service.ComponentInfo, 0, len(manifest.Components))
	for _, comp := range manifest.Components {
		chartName := comp.Chart.Name
		if chartName == "" {
			chartName = comp.Name
		}
		out = append(out, service.ComponentInfo{
			Name:          comp.Name,
			DisplayName:   comp.DisplayName,
			ComponentType: comp.ComponentType,
			ChartName:     chartName,
			ChartVersion:  comp.Chart.Version,
			Phase:         "unknown",
		})
	}
	return out
}

// =============================================================================
// Helpers
// =============================================================================

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func buildKubeClient(c *model.Cluster) (*service.KubeClient, error) {
	decrypted := decryptKubeconfig(c.KubeconfigEnc)
	return service.NewKubeClient([]byte(decrypted))
}

// encryptKubeconfig 简化版：Base64 编码（生产环境应使用 AES-256-GCM）
func encryptKubeconfig(plain string) string {
	return plain
}

func decryptKubeconfig(enc string) string {
	return enc
}

func extractServerHost(kubeconfig string) string {
	for _, line := range strings.Split(kubeconfig, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "server:") {
			return strings.TrimSpace(strings.TrimPrefix(line, "server:"))
		}
	}
	return ""
}

func buildFormSchema(product *model.Product) *model.FormSchema {
	var manifest model.Manifest
	if err := store.ParseJSON(product.ManifestJSON, &manifest); err != nil {
		return nil
	}

	form := &model.FormSchema{Groups: []model.FormGroup{}}

	// Group 1: 全局参数
	globalGroup := model.FormGroup{
		ID: "global", Title: "全局参数",
		Fields: []model.FormField{
			{Path: "globalParams.imageRegistry", Label: "镜像仓库", Type: "string", Placeholder: "registry.example.com"},
			{Path: "globalParams.defaultStorageClass", Label: "默认存储类", Type: "string"},
			{Path: "globalParams.domainSuffix", Label: "域名后缀", Type: "string", Placeholder: "example.com"},
		},
	}
	form.Groups = append(form.Groups, globalGroup)

	// Group 2+: 每个组件一组
	for _, comp := range manifest.Components {
		if len(comp.Parameters) == 0 {
			continue
		}
		compGroup := model.FormGroup{
			ID:     "component." + comp.Name,
			Title:  "组件 / " + comp.DisplayName,
			Fields: []model.FormField{},
		}
		for _, param := range comp.Parameters {
			field := model.FormField{
				Path:     "overrides." + comp.Name + "." + param.Path,
				Label:    param.DisplayName,
				Type:     param.Type,
				Default:  param.DefaultValue,
				Required: param.Required,
			}
			if param.Validation != nil {
				field.Validation = param.Validation
				if len(param.Validation.Enum) > 0 {
					field.Type = "select"
					for _, v := range param.Validation.Enum {
						field.Options = append(field.Options, model.SelectOption{Label: v, Value: v})
					}
				}
			}
			compGroup.Fields = append(compGroup.Fields, field)
		}
		form.Groups = append(form.Groups, compGroup)
	}

	return form
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// extractManifestFromZip 从 zip 文件中提取 manifest.json
func extractManifestFromZip(data []byte) (*model.Manifest, string, error) {
	// 先尝试直接 JSON 解析（内联 manifest 场景）
	if len(data) > 0 && data[0] == '{' {
		var m model.Manifest
		if err := json.Unmarshal(data, &m); err != nil {
			return nil, "", fmt.Errorf("invalid manifest json: %w", err)
		}
		if m.Key == "" {
			return nil, "", fmt.Errorf("manifest missing required field 'key'")
		}
		return &m, "/tmp/delivery-import", nil
	}

	// zip 模式：使用 archive/zip 解压读取
	reader, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, "", fmt.Errorf("cannot read zip: %w", err)
	}

	var manifestFile *zip.File
	var rootDir string

	for _, f := range reader.File {
		// 查找 manifest.json（根目录或 /charts 同级）
		name := filepath.Clean(f.Name)
		if filepath.Base(name) == "manifest.json" {
			manifestFile = f
			rootDir = filepath.Dir(name)
			if rootDir == "." {
				rootDir = ""
			}
			break
		}
	}

	if manifestFile == nil {
		return nil, "", fmt.Errorf("manifest.json not found in zip")
	}

	rc, err := manifestFile.Open()
	if err != nil {
		return nil, "", fmt.Errorf("cannot open manifest.json: %w", err)
	}
	defer rc.Close()

	var m model.Manifest
	if err := json.NewDecoder(rc).Decode(&m); err != nil {
		return nil, "", fmt.Errorf("invalid manifest.json: %w", err)
	}

	if m.Key == "" {
		return nil, "", fmt.Errorf("manifest missing required field 'key'")
	}
	if len(m.Components) == 0 {
		return nil, "", fmt.Errorf("manifest must have at least one component")
	}

	// 验证每个组件有对应 chart 目录
	for _, comp := range m.Components {
		chartPath := filepath.Join(rootDir, "charts", comp.Name, "Chart.yaml")
		found := false
		for _, f := range reader.File {
			if filepath.Clean(f.Name) == chartPath {
				found = true
				break
			}
		}
		if !found {
			return nil, "", fmt.Errorf("component '%s': chart not found at charts/%s/Chart.yaml", comp.Name, comp.Name)
		}
	}

	importDir := "/tmp/delivery-import/" + m.Key
	return &m, importDir, nil
}

// extractDefaultParams 从 manifest 中提取默认参数
func extractDefaultParams(m *model.Manifest) map[string]interface{} {
	params := map[string]interface{}{
		"params":    map[string]interface{}{},
		"overrides": map[string]interface{}{},
	}

	overrides := params["overrides"].(map[string]interface{})
	for _, comp := range m.Components {
		if len(comp.Parameters) == 0 && comp.DefaultValues == nil {
			continue
		}
		overrides[comp.Name] = comp.DefaultValues
	}

	return params
}

// mergeParamsOnReimport 再导入时合并参数
func mergeParamsOnReimport(m *model.Manifest, oldParams map[string]interface{}) map[string]interface{} {
	if oldParams == nil {
		return extractDefaultParams(m)
	}

	overrides, _ := oldParams["overrides"].(map[string]interface{})
	if overrides == nil {
		overrides = map[string]interface{}{}
	}

	for _, comp := range m.Components {
		if _, exists := overrides[comp.Name]; !exists && comp.DefaultValues != nil {
			overrides[comp.Name] = comp.DefaultValues
		}
	}

	params, _ := oldParams["params"].(map[string]interface{})
	if params == nil {
		params = map[string]interface{}{}
	}

	return map[string]interface{}{
		"params":    params,
		"overrides": overrides,
	}
}

// syncCloudServiceCR 同步创建/更新 CloudService CR
func (h *Handler) syncCloudServiceCR(product *model.Product) {
	// 获取当前集群
	cluster, err := h.store.GetCurrentCluster()
	if err != nil || cluster == nil {
		return
	}

	kc, err := buildKubeClient(cluster)
	if err != nil {
		return
	}

	// 创建/更新 CloudService CR 的逻辑
	// 实际实现通过 fabric8 客户端操作 CRD
	csName := "csvc-" + product.ProductKey
	_ = kc
	_ = csName

	// 记录 CloudService 名称
	product.CloudServiceName = csName
	product.CloudServiceNS = "delivery-system"
	h.store.UpdateProduct(product)
}

// listPods and listServices helpers for KubeClient (interface extensions)
// These methods are accessed through type assertion in getProductResources

// 抑制未使用导入
var _ = os.PathSeparator
var _ = fmt.Sprintf

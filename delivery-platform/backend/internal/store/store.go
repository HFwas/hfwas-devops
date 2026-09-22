package store

import (
	"database/sql"
	"encoding/json"
	"fmt"

	_ "github.com/mattn/go-sqlite3"

	"github.com/hfwas/delivery-platform/internal/model"
)

type Store struct {
	db *sql.DB
}

func New(dbPath string) (*Store, error) {
	db, err := sql.Open("sqlite3", dbPath+"?_journal_mode=WAL&_foreign_keys=on")
	if err != nil {
		return nil, fmt.Errorf("open db: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("ping db: %w", err)
	}
	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		return nil, fmt.Errorf("migrate: %w", err)
	}
	return s, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) migrate() error {
	migrations := []string{
		`CREATE TABLE IF NOT EXISTS delivery_cluster (
			id INTEGER NOT NULL PRIMARY KEY,
			name TEXT NOT NULL,
			server_host TEXT,
			kubeconfig_enc TEXT NOT NULL,
			is_current INTEGER NOT NULL DEFAULT 0,
			status TEXT NOT NULL DEFAULT 'UNKNOWN',
			version TEXT,
			deleted INTEGER NOT NULL DEFAULT 0,
			create_by INTEGER,
			update_by INTEGER,
			create_time TEXT NOT NULL DEFAULT (datetime('now')),
			update_time TEXT NOT NULL DEFAULT (datetime('now'))
		)`,
		`CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_name ON delivery_cluster (name) WHERE deleted = 0`,
		`CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_current ON delivery_cluster (is_current) WHERE deleted = 0 AND is_current = 1`,
		`CREATE TABLE IF NOT EXISTS delivery_product (
			id INTEGER NOT NULL PRIMARY KEY,
			product_key TEXT NOT NULL,
			display_name TEXT NOT NULL,
			package_version TEXT NOT NULL,
			package_dir TEXT NOT NULL,
			manifest_json TEXT NOT NULL,
			cloud_service_name TEXT,
			cloud_service_ns TEXT,
			params_json TEXT NOT NULL DEFAULT '{}',
			global_params_json TEXT NOT NULL DEFAULT '{}',
			status TEXT NOT NULL DEFAULT 'NOT_DEPLOYED',
			cluster_id INTEGER,
			target_ns TEXT,
			deleted INTEGER NOT NULL DEFAULT 0,
			create_by INTEGER,
			update_by INTEGER,
			create_time TEXT NOT NULL DEFAULT (datetime('now')),
			update_time TEXT NOT NULL DEFAULT (datetime('now'))
		)`,
		`CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_product_key ON delivery_product (product_key) WHERE deleted = 0`,
		`CREATE INDEX IF NOT EXISTS idx_delivery_product_deleted ON delivery_product (deleted)`,
		`CREATE TABLE IF NOT EXISTS delivery_deployment (
			id INTEGER NOT NULL PRIMARY KEY,
			product_id INTEGER NOT NULL,
			cluster_id INTEGER NOT NULL,
			action TEXT NOT NULL,
			package_version TEXT NOT NULL,
			params_snapshot_json TEXT NOT NULL,
			global_params_snapshot TEXT NOT NULL,
			app_name TEXT,
			app_ns TEXT,
			status TEXT NOT NULL DEFAULT 'PENDING',
			error_message TEXT,
			started_at TEXT,
			finished_at TEXT,
			create_by INTEGER,
			create_time TEXT NOT NULL DEFAULT (datetime('now'))
		)`,
		`CREATE INDEX IF NOT EXISTS idx_delivery_deployment_product ON delivery_deployment (product_id, id DESC)`,
	}
	for _, m := range migrations {
		if _, err := s.db.Exec(m); err != nil {
			return fmt.Errorf("exec migration: %s: %w", m[:60], err)
		}
	}
	return nil
}

// =============================================================================
// Cluster
// =============================================================================

func (s *Store) ListClusters() ([]model.Cluster, error) {
	rows, err := s.db.Query(`SELECT id, name, server_host, is_current, status, COALESCE(version,''), create_time, update_time FROM delivery_cluster WHERE deleted = 0 ORDER BY id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var list = make([]model.Cluster, 0)
	for rows.Next() {
		var c model.Cluster
		if err := rows.Scan(&c.ID, &c.Name, &c.ServerHost, &c.IsCurrent, &c.Status, &c.Version, &c.CreateTime, &c.UpdateTime); err != nil {
			return nil, err
		}
		list = append(list, c)
	}
	return list, nil
}

func (s *Store) GetCluster(id int64) (*model.Cluster, error) {
	c := &model.Cluster{}
	err := s.db.QueryRow(`SELECT id, name, server_host, kubeconfig_enc, is_current, status, COALESCE(version,''), create_time, update_time FROM delivery_cluster WHERE id = ? AND deleted = 0`, id).
		Scan(&c.ID, &c.Name, &c.ServerHost, &c.KubeconfigEnc, &c.IsCurrent, &c.Status, &c.Version, &c.CreateTime, &c.UpdateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return c, err
}

func (s *Store) GetClusterByName(name string) (*model.Cluster, error) {
	c := &model.Cluster{}
	err := s.db.QueryRow(`SELECT id, name, server_host, kubeconfig_enc, is_current, status, COALESCE(version,''), create_time, update_time FROM delivery_cluster WHERE name = ? AND deleted = 0`, name).
		Scan(&c.ID, &c.Name, &c.ServerHost, &c.KubeconfigEnc, &c.IsCurrent, &c.Status, &c.Version, &c.CreateTime, &c.UpdateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return c, err
}

func (s *Store) GetCurrentCluster() (*model.Cluster, error) {
	c := &model.Cluster{}
	err := s.db.QueryRow(`SELECT id, name, server_host, kubeconfig_enc, is_current, status, COALESCE(version,''), create_time, update_time FROM delivery_cluster WHERE is_current = 1 AND deleted = 0`).
		Scan(&c.ID, &c.Name, &c.ServerHost, &c.KubeconfigEnc, &c.IsCurrent, &c.Status, &c.Version, &c.CreateTime, &c.UpdateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return c, err
}

func (s *Store) CreateCluster(c *model.Cluster) error {
	result, err := s.db.Exec(`INSERT INTO delivery_cluster (name, server_host, kubeconfig_enc, is_current, status, version, create_by, update_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		c.Name, c.ServerHost, c.KubeconfigEnc, boolToInt(c.IsCurrent), c.Status, c.Version, c.CreateBy, c.UpdateBy)
	if err != nil {
		return err
	}
	id, _ := result.LastInsertId()
	c.ID = id
	return nil
}

func (s *Store) UpdateClusterStatus(id int64, status, version string) error {
	_, err := s.db.Exec(`UPDATE delivery_cluster SET status = ?, version = ?, update_time = datetime('now') WHERE id = ?`, status, version, id)
	return err
}

func (s *Store) ClearCurrentCluster() error {
	_, err := s.db.Exec(`UPDATE delivery_cluster SET is_current = 0 WHERE is_current = 1 AND deleted = 0`)
	return err
}

func (s *Store) SetCurrentCluster(id int64) error {
	_, err := s.db.Exec(`UPDATE delivery_cluster SET is_current = 1, update_time = datetime('now') WHERE id = ? AND deleted = 0`, id)
	return err
}

func (s *Store) SoftDeleteCluster(id int64) error {
	_, err := s.db.Exec(`UPDATE delivery_cluster SET deleted = 1, is_current = 0, update_time = datetime('now') WHERE id = ?`, id)
	return err
}

func (s *Store) CountProductsOnCluster(id int64) (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM delivery_product WHERE cluster_id = ? AND deleted = 0 AND status != 'NOT_DEPLOYED'`, id).Scan(&n)
	return n, err
}

// =============================================================================
// Product
// =============================================================================

func (s *Store) ListProducts(pageNo, pageSize int, keyword string) ([]model.ProductListResult, int64, error) {
	where := "WHERE p.deleted = 0"
	args := []interface{}{}
	if keyword != "" {
		where += " AND (p.display_name LIKE ? OR p.product_key LIKE ?)"
		kw := "%" + keyword + "%"
		args = append(args, kw, kw)
	}

	var total int64
	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM delivery_product p %s", where)
	if err := s.db.QueryRow(countQuery, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	offset := (pageNo - 1) * pageSize
	query := fmt.Sprintf(`
		SELECT p.id, p.product_key, p.display_name, p.package_version, p.status,
			COALESCE(c.name,'') as cluster_name, COALESCE(p.target_ns,''),
			p.create_time
		FROM delivery_product p
		LEFT JOIN delivery_cluster c ON c.id = p.cluster_id
		%s
		ORDER BY p.id DESC LIMIT ? OFFSET ?`, where)
	args = append(args, pageSize, offset)

	rows, err := s.db.Query(query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var list []model.ProductListResult
	for rows.Next() {
		var r model.ProductListResult
		if err := rows.Scan(&r.ID, &r.ProductKey, &r.DisplayName, &r.PackageVersion, &r.Status,
			&r.ClusterName, &r.TargetNS, &r.LastDeployTime); err != nil {
			return nil, 0, err
		}
		list = append(list, r)
	}
	return list, total, nil
}

func (s *Store) GetProduct(id int64) (*model.Product, error) {
	p := &model.Product{}
	err := s.db.QueryRow(`
		SELECT id, product_key, display_name, package_version, package_dir, manifest_json,
			COALESCE(cloud_service_name,''), COALESCE(cloud_service_ns,''),
			params_json, global_params_json, status, cluster_id, COALESCE(target_ns,''),
			create_time, update_time
		FROM delivery_product WHERE id = ? AND deleted = 0`, id).
		Scan(&p.ID, &p.ProductKey, &p.DisplayName, &p.PackageVersion, &p.PackageDir,
			&p.ManifestJSON, &p.CloudServiceName, &p.CloudServiceNS,
			&p.ParamsJSON, &p.GlobalParamsJSON, &p.Status, &p.ClusterID, &p.TargetNS,
			&p.CreateTime, &p.UpdateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (s *Store) GetProductByKey(key string) (*model.Product, error) {
	p := &model.Product{}
	err := s.db.QueryRow(`
		SELECT id, product_key, display_name, package_version, package_dir, manifest_json,
			COALESCE(cloud_service_name,''), COALESCE(cloud_service_ns,''),
			params_json, global_params_json, status, cluster_id, COALESCE(target_ns,''),
			create_time, update_time
		FROM delivery_product WHERE product_key = ? AND deleted = 0`, key).
		Scan(&p.ID, &p.ProductKey, &p.DisplayName, &p.PackageVersion, &p.PackageDir,
			&p.ManifestJSON, &p.CloudServiceName, &p.CloudServiceNS,
			&p.ParamsJSON, &p.GlobalParamsJSON, &p.Status, &p.ClusterID, &p.TargetNS,
			&p.CreateTime, &p.UpdateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return p, err
}

func (s *Store) CreateProduct(p *model.Product) error {
	result, err := s.db.Exec(`
		INSERT INTO delivery_product (product_key, display_name, package_version, package_dir, manifest_json,
			params_json, global_params_json, status, create_by, update_by)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		p.ProductKey, p.DisplayName, p.PackageVersion, p.PackageDir, p.ManifestJSON,
		p.ParamsJSON, p.GlobalParamsJSON, p.Status, p.CreateBy, p.UpdateBy)
	if err != nil {
		return err
	}
	id, _ := result.LastInsertId()
	p.ID = id
	return nil
}

func (s *Store) UpdateProduct(p *model.Product) error {
	_, err := s.db.Exec(`
		UPDATE delivery_product SET display_name=?, package_version=?, package_dir=?, manifest_json=?,
			cloud_service_name=?, cloud_service_ns=?, params_json=?, global_params_json=?,
			status=?, cluster_id=?, target_ns=?, update_by=?, update_time=datetime('now')
		WHERE id=?`,
		p.DisplayName, p.PackageVersion, p.PackageDir, p.ManifestJSON,
		p.CloudServiceName, p.CloudServiceNS, p.ParamsJSON, p.GlobalParamsJSON,
		p.Status, p.ClusterID, p.TargetNS, p.UpdateBy, p.ID)
	return err
}

func (s *Store) UpdateProductStatus(id int64, status string) error {
	_, err := s.db.Exec(`UPDATE delivery_product SET status=?, update_time=datetime('now') WHERE id=?`, status, id)
	return err
}

// =============================================================================
// Deployment
// =============================================================================

func (s *Store) CreateDeployment(d *model.Deployment) error {
	result, err := s.db.Exec(`
		INSERT INTO delivery_deployment (product_id, cluster_id, action, package_version,
			params_snapshot_json, global_params_snapshot, status, started_at, create_by)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		d.ProductID, d.ClusterID, d.Action, d.PackageVersion,
		d.ParamsSnapshotJSON, d.GlobalSnapshotJSON, d.Status, d.StartedAt, d.CreateBy)
	if err != nil {
		return err
	}
	id, _ := result.LastInsertId()
	d.ID = id
	return nil
}

func (s *Store) UpdateDeploymentStatus(id int64, status, appName, appNs, errMsg string) error {
	_, err := s.db.Exec(`
		UPDATE delivery_deployment SET status=?, app_name=?, app_ns=?, error_message=?,
			finished_at=CASE WHEN ? IN ('SUCCEEDED','FAILED','CANCELLED') THEN datetime('now') ELSE finished_at END
		WHERE id=?`, status, appName, appNs, errMsg, status, id)
	return err
}

func (s *Store) GetDeployment(id int64) (*model.Deployment, error) {
	d := &model.Deployment{}
	err := s.db.QueryRow(`
		SELECT id, product_id, cluster_id, action, package_version, status,
			COALESCE(error_message,''), COALESCE(started_at,''), COALESCE(finished_at,''),
			COALESCE(app_name,''), COALESCE(app_ns,''), params_snapshot_json, global_params_snapshot, create_time
		FROM delivery_deployment WHERE id = ?`, id).
		Scan(&d.ID, &d.ProductID, &d.ClusterID, &d.Action, &d.PackageVersion, &d.Status,
			&d.ErrorMessage, &d.StartedAt, &d.FinishedAt,
			&d.AppName, &d.AppNS, &d.ParamsSnapshotJSON, &d.GlobalSnapshotJSON, &d.CreateTime)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	return d, err
}

func (s *Store) ListDeployments(productID int64) ([]model.Deployment, error) {
	rows, err := s.db.Query(`SELECT id, product_id, cluster_id, action, package_version, status,
		COALESCE(error_message,''), COALESCE(started_at,''), COALESCE(finished_at,''),
		COALESCE(app_name,''), COALESCE(app_ns,''), create_time
		FROM delivery_deployment WHERE product_id=? ORDER BY id DESC LIMIT 50`, productID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var list []model.Deployment
	for rows.Next() {
		var d model.Deployment
		if err := rows.Scan(&d.ID, &d.ProductID, &d.ClusterID, &d.Action, &d.PackageVersion, &d.Status,
			&d.ErrorMessage, &d.StartedAt, &d.FinishedAt, &d.AppName, &d.AppNS, &d.CreateTime); err != nil {
			return nil, err
		}
		list = append(list, d)
	}
	return list, nil
}

// =============================================================================
// Platform stats
// =============================================================================

type PlatformStats struct {
	ClusterCount   int            `json:"clusterCount"`
	CurrentCluster *model.Cluster `json:"currentCluster,omitempty"`
	ProductCounts  ProductCounts  `json:"productCounts"`
}

type ProductCounts struct {
	Total       int `json:"total"`
	Deployed    int `json:"deployed"`
	NotDeployed int `json:"notDeployed"`
	Failed      int `json:"failed"`
}

func (s *Store) GetPlatformStats() (*PlatformStats, error) {
	stats := &PlatformStats{}
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM delivery_cluster WHERE deleted = 0`).Scan(&stats.ClusterCount)
	stats.CurrentCluster, _ = s.GetCurrentCluster()
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM delivery_product WHERE deleted = 0`).Scan(&stats.ProductCounts.Total)
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM delivery_product WHERE deleted = 0 AND status IN ('READY','DEGRADED')`).Scan(&stats.ProductCounts.Deployed)
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM delivery_product WHERE deleted = 0 AND status = 'NOT_DEPLOYED'`).Scan(&stats.ProductCounts.NotDeployed)
	_ = s.db.QueryRow(`SELECT COUNT(*) FROM delivery_product WHERE deleted = 0 AND status = 'FAILED'`).Scan(&stats.ProductCounts.Failed)
	return stats, nil
}

// =============================================================================
// Helpers
// =============================================================================

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

// JSON helpers
func ToJSON(v interface{}) string {
	b, _ := json.Marshal(v)
	return string(b)
}

func ParseJSON(data string, v interface{}) error {
	return json.Unmarshal([]byte(data), v)
}

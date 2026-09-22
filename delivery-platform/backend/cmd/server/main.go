package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"

	"github.com/hfwas/delivery-platform/internal/api"
	"github.com/hfwas/delivery-platform/internal/store"
)

func main() {
	// 数据目录
	dataDir := os.Getenv("DELIVERY_DATA_DIR")
	if dataDir == "" {
		home, _ := os.UserHomeDir()
		dataDir = filepath.Join(home, ".delivery-platform")
	}
	os.MkdirAll(dataDir, 0755)

	// 初始化数据库
	dbPath := filepath.Join(dataDir, "delivery.db")
	s, err := store.New(dbPath)
	if err != nil {
		log.Fatalf("Failed to initialize store: %v", err)
	}
	defer s.Close()

	// 包存储目录
	packageDir := filepath.Join(dataDir, "packages")
	os.MkdirAll(packageDir, 0755)

	// 初始化路由
	r := chi.NewRouter()
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"http://localhost:5173", "http://localhost:3000"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Content-Type", "Authorization"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// 健康检查
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"status":"ok"}`))
	})

	// 注册业务路由
	handler := api.New(s)
	handler.RegisterRoutes(r)

	// 启动服务
	port := os.Getenv("PORT")
	if port == "" {
		port = "8180"
	}
	addr := fmt.Sprintf(":%s", port)
	log.Printf("Delivery platform API starting on %s", addr)
	log.Printf("Data directory: %s", dataDir)

	if err := http.ListenAndServe(addr, r); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
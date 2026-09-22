package v1

import (
	"k8s.io/apimachinery/pkg/runtime/schema"
	"sigs.k8s.io/controller-runtime/pkg/scheme"
)

var (
	// GroupVersion 是 API 分组版本。
	GroupVersion = schema.GroupVersion{
		Group:   "delivery.hfwas.io",
		Version: "v1",
	}

	// SchemeBuilder 用于注册 CRD 类型。
	SchemeBuilder = &scheme.Builder{GroupVersion: GroupVersion}

	// AddToScheme 将 CRD 类型注册到 schema。
	AddToScheme = SchemeBuilder.AddToScheme
)

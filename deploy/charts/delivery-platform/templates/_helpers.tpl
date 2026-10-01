{{/*
资源名固定，与裸清单和前端 nginx 反代一致，不拼 Release 名。
*/}}
{{- define "delivery-platform.namespace" -}}
{{- .Release.Namespace }}
{{- end }}

{{- define "delivery-platform.labels" -}}
app.kubernetes.io/part-of: delivery-platform
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end }}

{{- define "delivery-platform.backendSelector" -}}
app.kubernetes.io/name: delivery-backend
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "delivery-platform.frontendSelector" -}}
app.kubernetes.io/name: delivery-frontend
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

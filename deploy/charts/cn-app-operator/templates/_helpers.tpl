{{- define "cn-app-operator.namespace" -}}
{{- .Release.Namespace }}
{{- end }}

{{- define "cn-app-operator.labels" -}}
app.kubernetes.io/name: cn-app-operator
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: delivery-platform
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
delivery.hfwas.io/managed-by: cn-app-operator
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end }}

{{- define "cn-app-operator.selectorLabels" -}}
app.kubernetes.io/name: cn-app-operator
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

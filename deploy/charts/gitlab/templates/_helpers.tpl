{{- define "gitlab.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "gitlab.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- include "gitlab.name" . -}}
{{- end -}}
{{- end -}}

{{- define "gitlab.labels" -}}
app.kubernetes.io/name: {{ include "gitlab.name" . }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end -}}

{{- define "gitlab.selectorLabels" -}}
app.kubernetes.io/name: {{ include "gitlab.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "gitlab.omnibusConfig" -}}
external_url '{{ .Values.externalUrl }}'
nginx['listen_port'] = 80
nginx['listen_https'] = false
letsencrypt['enable'] = false
prometheus_monitoring['enable'] = false
gitlab_rails['gitlab_shell_ssh_port'] = {{ .Values.sshPort }}
gitlab_rails['initial_root_password'] = '{{ .Values.rootPassword }}'
puma['worker_processes'] = 2
sidekiq['max_concurrency'] = 10
{{- if .Values.extraOmnibusConfig }}
{{ .Values.extraOmnibusConfig }}
{{- end }}
{{- end -}}

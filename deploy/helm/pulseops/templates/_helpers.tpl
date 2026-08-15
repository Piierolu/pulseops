{{- define "pulseops.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "pulseops.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "pulseops.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "pulseops.labels" -}}
app.kubernetes.io/name: {{ include "pulseops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "pulseops.image" -}}
{{- printf "%s/%s:%s" .root.Values.global.imageRegistry .image .root.Values.global.imageTag -}}
{{- end -}}

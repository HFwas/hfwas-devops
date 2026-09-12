import type { JobKind } from '@/modules/pipeline/types/pipeline'

export interface JobKindMeta {
  value: JobKind
  label: string
  group: string
  description: string
  hint: string
  requiresCommand: boolean
  defaultCommand: string
}

export const JOB_KIND_CATALOG: JobKindMeta[] = [
  {
    value: 'CLONE',
    label: '代码克隆',
    group: '代码',
    description: '从 Git 仓库拉取代码',
    hint: 'Clone 命令由平台生成，在流水线里填写仓库与凭证即可。',
    requiresCommand: false,
    defaultCommand: '',
  },
  {
    value: 'BUILD',
    label: '构建',
    group: '构建',
    description: '编译与打包源码',
    hint: '',
    requiresCommand: true,
    defaultCommand: '',
  },
  {
    value: 'IMAGE',
    label: '镜像构建',
    group: '构建',
    description: 'Buildah 多架构构建并推送镜像',
    hint: '填写 DEST / IMAGE_PLATFORMS / DOCKERFILE；可选 COSIGN_PRIVATE_KEY。支持 linux/amd64,linux/arm64 等多架构。',
    requiresCommand: true,
    defaultCommand:
      'export DEST=registry.example.com/app:tag\n'
      + 'export IMAGE_PLATFORMS=linux/amd64,linux/arm64\n'
      + 'export DOCKERFILE=Dockerfile',
  },
  {
    value: 'LINT_SEMGREP',
    label: 'Semgrep 检查',
    group: '质量控制',
    description: 'Semgrep 静态检查',
    hint: '填写 Semgrep CLI。',
    requiresCommand: true,
    defaultCommand: 'semgrep scan --error --config=auto .',
  },
  {
    value: 'LINT_SONAR',
    label: 'Sonar 检查',
    group: '质量控制',
    description: 'SonarScanner 静态检查',
    hint: '填写 SONAR_HOST_URL / SONAR_TOKEN / SONAR_PROJECT_KEY。',
    requiresCommand: true,
    defaultCommand: `export SONAR_HOST_URL=https://sonar.example.com
export SONAR_TOKEN=
export SONAR_PROJECT_KEY=app`,
  },
  {
    value: 'FORMAT',
    label: '代码格式化',
    group: '质量控制',
    description: '自动格式化代码并提交回仓库',
    hint: '选择技术栈并填写格式化命令。JS/TS → npx prettier --write .，Java → mvn spotless:apply，Go → gofmt -w .，Python → black .',
    requiresCommand: true,
    defaultCommand: 'npx prettier --write .',
  },
  {
    value: 'DEPENDENCY_ANALYSIS',
    label: '依赖分析',
    group: '质量控制',
    description: '生成 CycloneDX 格式的依赖清单（SBOM），为漏洞扫描提供精确的依赖树',
    hint: 'Java 项目使用 CycloneDX Maven Plugin，其他语言使用 cdxgen。产出 target/sbom.json。',
    requiresCommand: true,
    defaultCommand: 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.10.0:makeAggregateBom -Dcyclonedx.outputFormat=json -Dcyclonedx.outputName=sbom --no-transfer-progress -q',
  },
  {
    value: 'SCAN',
    label: '安全扫描',
    group: '质量控制',
    description: '依赖与文件系统漏洞扫描',
    hint: '',
    requiresCommand: true,
    defaultCommand: 'trivy fs --exit-code 1 --scanners vuln,secret,misconfig .',
  },
  {
    value: 'PACKAGE',
    label: '打包',
    group: '制品',
    description: '产出可分发制品',
    hint: '',
    requiresCommand: true,
    defaultCommand: '',
  },
  {
    value: 'PUBLISH',
    label: '发布制品',
    group: '制品',
    description: '把制品发布到仓库',
    hint: '',
    requiresCommand: true,
    defaultCommand: '',
  },
  {
    value: 'UPLOAD',
    label: '上传对象存储',
    group: '制品',
    description: 'rclone 上传到对象存储',
    hint: '',
    requiresCommand: true,
    defaultCommand:
      'rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint="${S3_ENDPOINT}" --s3-access-key-id="${S3_ACCESS_KEY}" --s3-secret-access-key="${S3_SECRET_KEY}"',
  },
  {
    value: 'DEPLOY',
    label: '部署',
    group: '部署',
    description: '发布到 Kubernetes 或其他环境',
    hint: '',
    requiresCommand: true,
    defaultCommand: 'kubectl apply -f k8s/',
  },
  {
    value: 'TEST',
    label: '测试',
    group: '测试',
    description: '运行单元 / 集成测试',
    hint: '',
    requiresCommand: true,
    defaultCommand: '',
  },
  {
    value: 'CUSTOM',
    label: '自定义命令',
    group: '命令',
    description: '在工具链镜像里执行任意命令',
    hint: '',
    requiresCommand: true,
    defaultCommand: 'echo ok',
  },
  {
    value: 'APPROVAL',
    label: '人工卡点',
    group: '流程',
    description: '运行到此处暂停，需人工通过',
    hint: '运行到此处会暂停，需在运行页点通过。审批节点必须单独成阶段。',
    requiresCommand: false,
    defaultCommand: '',
  },
  {
    value: 'NOTIFY',
    label: '通知',
    group: '流程',
    description: 'Webhook / HTTP 通知',
    hint: '',
    requiresCommand: true,
    defaultCommand: `curl -fsS -X POST 'https://example.com/hook' -H 'Content-Type: application/json' -d '{"status":"done"}'`,
  },
]

export const JOB_KIND_GROUPS = ['代码', '构建', '质量控制', '制品', '部署', '测试', '命令', '流程'] as const

export const JOB_KIND_OPTIONS = JOB_KIND_CATALOG.map((item) => ({
  value: item.value,
  label: item.label,
  requiresCommand: item.requiresCommand,
  defaultCommand: item.defaultCommand,
}))

export function jobKindMeta(kind?: string | null): JobKindMeta | undefined {
  return JOB_KIND_CATALOG.find((item) => item.value === kind)
}

export function jobKindLabel(kind?: string | null): string {
  return jobKindMeta(kind)?.label ?? kind ?? ''
}

export function requiresCommand(kind?: string | null): boolean {
  return jobKindMeta(kind)?.requiresCommand ?? true
}


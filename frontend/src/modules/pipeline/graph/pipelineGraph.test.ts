import { describe, expect, it } from 'vitest'
import {
  canDeleteJob,
  canDeleteStage,
  createDefaultGraph,
  refreshDefaultCommands,
  repoShortName,
  stackSummary,
} from './pipelineGraph'
import type { ToolchainOption } from '@/modules/pipeline/types/pipeline'

const java21: ToolchainOption = {
  stack: 'JAVA_MAVEN',
  runtimeVersion: '21',
  toolVersion: '3.9',
  image: 'maven:3.9.9-eclipse-temurin-21',
  buildCommand: 'mvn -B -DskipTests package',
  testCommand: 'mvn -B test',
}

const node22: ToolchainOption = {
  stack: 'NODE',
  runtimeVersion: '22',
  toolVersion: 'NPM',
  image: 'node:22-bookworm',
  buildCommand: 'npm ci',
  testCommand: 'npm test',
}

describe('pipeline default graph', () => {
  it('creates clone / build / test columns with clone first', () => {
    const graph = createDefaultGraph(java21)
    expect(graph).toHaveLength(3)
    expect(graph[0].name).toBe('clone')
    expect(graph[0].jobs[0].kind).toBe('CLONE')
    expect(graph[1].jobs[0].command).toBe(java21.buildCommand)
    expect(graph[2].jobs[0].command).toBe(java21.testCommand)
    expect(canDeleteJob(graph[0].jobs[0])).toBe(false)
    expect(canDeleteStage(graph[0])).toBe(false)
    expect(canDeleteStage(graph[1])).toBe(true)
  })

  it('refreshes build/test defaults when switching stack', () => {
    const graph = createDefaultGraph(java21)
    const next = refreshDefaultCommands(graph, java21, node22)
    expect(next[1].jobs[0].command).toBe('npm ci')
    expect(next[2].jobs[0].command).toBe('npm test')
  })

  it('keeps custom commands when they are no longer the old default', () => {
    const graph = createDefaultGraph(java21)
    graph[1].jobs[0].command = 'mvn -B package -DskipTests=false'
    const next = refreshDefaultCommands(graph, java21, node22)
    expect(next[1].jobs[0].command).toBe('mvn -B package -DskipTests=false')
  })

  it('summarizes repo and stack', () => {
    expect(repoShortName('https://github.com/acme/demo.git')).toBe('acme/demo')
    expect(repoShortName('https://gitlab.example.com/group/proj.git')).toBe('group/proj')
    expect(stackSummary('JAVA_MAVEN', '21', '3.9')).toBe('Java 21 / Maven 3.9')
  })
})

import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: 'http://localhost:8000/auth',
  realm: 'hfwas-devops',
  clientId: 'hfwas-devops-web',
})

let initialized = false

export function getKeycloak() {
  return keycloak
}

export function isAuthenticated() {
  return !!keycloak.authenticated
}

export function appRedirectUri(path?: string) {
  const origin = window.location.origin
  if (!path || path === '/') {
    return `${origin}/workbench`
  }
  return `${origin}${path.startsWith('/') ? path : `/${path}`}`
}

export async function initKeycloak() {
  if (initialized) {
    return keycloak.authenticated === true
  }
  const ok = await keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    redirectUri: appRedirectUri(window.location.pathname),
  })
  initialized = true
  return ok
}

export async function getToken(): Promise<string | undefined> {
  if (!keycloak.authenticated) {
    return undefined
  }
  try {
    await keycloak.updateToken(30)
  } catch {
    await login()
    return undefined
  }
  return keycloak.token
}

export function login(redirectUri?: string) {
  return keycloak.login({
    redirectUri: redirectUri ?? appRedirectUri(window.location.pathname),
  })
}

export function logout(redirectUri?: string) {
  return keycloak.logout({
    redirectUri: redirectUri ?? appRedirectUri('/workbench'),
  })
}

import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: 'http://localhost:8000/auth',
  realm: 'hfwas-devops',
  clientId: 'hfwas-devops-web',
})

let initialized = false
let loginRedirecting = false

export function getKeycloak() {
  return keycloak
}

export function isAuthenticated() {
  return !!keycloak.authenticated
}

export function appRedirectUri(path?: string) {
  const origin = window.location.origin
  const noHash = (path ?? '').split('#')[0]
  if (!noHash || noHash === '/') {
    return `${origin}/workbench`
  }
  return `${origin}${noHash.startsWith('/') ? noHash : `/${noHash}`}`
}

export async function initKeycloak() {
  if (initialized) {
    return keycloak.authenticated === true
  }
  const ok = await keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    redirectUri: appRedirectUri(window.location.pathname),
    locale: 'zh-CN',
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
  if (loginRedirecting) {
    return Promise.resolve()
  }
  loginRedirecting = true
  return keycloak.login({
    locale: 'zh-CN',
    redirectUri: redirectUri ?? appRedirectUri(window.location.pathname),
  })
}

export function logout(redirectUri?: string) {
  return keycloak.logout({
    redirectUri: redirectUri ?? appRedirectUri('/workbench'),
  })
}

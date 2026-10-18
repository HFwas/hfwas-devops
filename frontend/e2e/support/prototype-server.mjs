import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '../../../docs/pipeline/prototype')
const host = '127.0.0.1'
const port = Number(process.env.PLAYWRIGHT_PROTOTYPE_PORT || 8099)

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.json': 'application/json; charset=utf-8',
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${host}:${port}`)
    const pathname = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)
    const filePath = path.resolve(root, `.${pathname}`)
    if (filePath !== root && !filePath.startsWith(root + path.sep)) {
      res.writeHead(403)
      res.end('Forbidden')
      return
    }
    const body = await readFile(filePath)
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(filePath)] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    })
    res.end(body)
  } catch {
    res.writeHead(404)
    res.end('Not found')
  }
})

server.listen(port, host, () => {
  console.log(`prototype server: http://${host}:${port}/index.html`)
})

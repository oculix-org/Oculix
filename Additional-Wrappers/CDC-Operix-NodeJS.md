# CDC — Operix-JS (Node.js)
## Node.js wrapper for OculiX via java-caller

**Auteur :** Julien Mer — JMer Consulting
**Date :** 19 avril 2026
**Statut :** A implementer
**Repo cible :** oculix-org/operix-js
**npm :** oculix
**Dependance :** oculixapi (Maven Central)

---

## 1. Probleme

Node.js est l'ecosysteme dominant pour le testing web (Playwright, Cypress, Jest, Mocha).
Mais aucun de ces outils ne fait du visual testing sur desktop, VNC, Android, kiosque, POS.
Playwright est browser-only. Cypress est browser-only. Pour tester une app native Windows,
un terminal VNC, un mainframe — le dev Node n'a rien.

## 2. Solution

Un package npm `oculix` qui expose toute l'API OculiX en JavaScript/TypeScript via
un pont Java. L'utilisateur tape `npm install oculix` et combine visual testing natif
avec ses outils Node existants.

```bash
npm install oculix
```

```javascript
const { Screen, App, Pattern } = require('oculix');

const screen = new Screen();
await screen.click("button.png");
const app = await App.open("notepad");
```

## 3. Architecture

```
+---------------------+              +---------------------+
|   Node.js process   |  java-caller |   JVM process       |
|                     |   (child)    |                     |
|   const screen =    |<------------>|   oculixapi.jar     |
|   new Screen()      |   stdin/out  |   + JSON-RPC server |
|                     |              |                     |
+---------------------+              +---------------------+
```

**Pont Java pour Node.js :** `java-caller` (npm package) lance la JVM comme child process
et communique via stdin/stdout en JSON. Alternative : `node-java-bridge` qui utilise JNI
(plus performant, plus complexe a builder cross-platform).

**Recommandation :** `java-caller` pour la V1 (simple, cross-platform, zero native deps).
Migrer vers `node-java-bridge` si la latence pose probleme.

## 4. Structure du repo

```
oculix-org/operix-js/
|-- README.md
|-- LICENSE (MIT)
|-- package.json
|-- tsconfig.json
|-- src/
|   |-- index.ts              # exports principaux
|   |-- gateway.ts            # JVM lifecycle (start/stop/download JAR)
|   |-- screen.ts             # wrapper Screen
|   |-- region.ts             # wrapper Region
|   |-- pattern.ts            # wrapper Pattern
|   |-- app.ts                # wrapper App
|   |-- vnc.ts                # wrapper VNCScreen, SSHTunnel
|   |-- adb.ts                # wrapper ADBScreen
|   |-- ocr.ts                # wrapper OCR
|   +-- keys.ts               # constantes Key, KeyModifier
|-- java/
|   +-- OculixJsonRpcServer.java  # serveur JSON-RPC dans la JVM
|-- tests/
|   |-- gateway.test.ts
|   |-- screen.test.ts
|   +-- app.test.ts
+-- examples/
    |-- basic.ts
    |-- vnc-remote.ts
    +-- with-playwright.ts
```

## 5. Composants detailles

### 5.1 gateway.ts — JVM lifecycle

```typescript
import { spawn, ChildProcess } from 'child_process';
import * as path from 'path';
import * as fs from 'fs';
import * as https from 'https';

const JAR_DIR = path.join(require('os').homedir(), '.oculix', 'lib');
const JAR_NAME = 'oculixapi-3.0.2.jar';
const JAR_URL = 'https://repo1.maven.org/maven2/io/github/oculix-org/oculixapi/3.0.2/oculixapi-3.0.2.jar';

let jvmProcess: ChildProcess | null = null;
let requestId = 0;
let pendingRequests = new Map<number, { resolve: Function, reject: Function }>();

async function ensureJar(): Promise<string> {
  const jarPath = path.join(JAR_DIR, JAR_NAME);
  if (!fs.existsSync(jarPath)) {
    fs.mkdirSync(JAR_DIR, { recursive: true });
    console.log(`[OculiX] Downloading ${JAR_NAME}...`);
    await downloadFile(JAR_URL, jarPath);
    console.log(`[OculiX] Saved to ${jarPath}`);
  }
  return jarPath;
}

export async function start(): Promise<void> {
  if (jvmProcess) return;
  const jarPath = await ensureJar();

  jvmProcess = spawn('java', ['-cp', jarPath, 'org.sikuli.script.OculixJsonRpcServer'], {
    stdio: ['pipe', 'pipe', 'pipe']
  });

  jvmProcess.stdout!.on('data', (data: Buffer) => {
    const lines = data.toString().split('\n').filter(l => l.trim());
    for (const line of lines) {
      try {
        const response = JSON.parse(line);
        const pending = pendingRequests.get(response.id);
        if (pending) {
          pendingRequests.delete(response.id);
          if (response.error) pending.reject(new Error(response.error));
          else pending.resolve(response.result);
        }
      } catch {}
    }
  });

  process.on('exit', stop);
  await new Promise(resolve => setTimeout(resolve, 3000));
}

export async function call(className: string, method: string, ...args: any[]): Promise<any> {
  if (!jvmProcess) await start();
  const id = ++requestId;
  const request = JSON.stringify({ id, class: className, method, args }) + '\n';

  return new Promise((resolve, reject) => {
    pendingRequests.set(id, { resolve, reject });
    jvmProcess!.stdin!.write(request);
    setTimeout(() => {
      if (pendingRequests.has(id)) {
        pendingRequests.delete(id);
        reject(new Error(`Timeout calling ${className}.${method}`));
      }
    }, 30000);
  });
}

export function stop(): void {
  if (jvmProcess) {
    jvmProcess.kill();
    jvmProcess = null;
  }
}
```

### 5.2 index.ts — API publique

```typescript
import { call, start, stop } from './gateway';

export class Screen {
  async click(target: string): Promise<void> {
    await call('Screen', 'click', target);
  }

  async doubleClick(target: string): Promise<void> {
    await call('Screen', 'doubleClick', target);
  }

  async rightClick(target: string): Promise<void> {
    await call('Screen', 'rightClick', target);
  }

  async type(text: string): Promise<void> {
    await call('Screen', 'type', text);
  }

  async wait(target: string, timeout: number = 10): Promise<void> {
    await call('Screen', 'wait', target, timeout);
  }

  async exists(target: string, timeout: number = 3): Promise<boolean> {
    return await call('Screen', 'exists', target, timeout);
  }

  async find(target: string): Promise<any> {
    return await call('Screen', 'find', target);
  }

  async text(): Promise<string> {
    return await call('Screen', 'text');
  }

  async capture(): Promise<string> {
    return await call('Screen', 'capture');
  }
}

export class App {
  private name: string;
  constructor(name: string) { this.name = name; }

  static async open(path: string): Promise<App> {
    await call('App', 'open', path);
    return new App(path);
  }

  async focus(): Promise<void> {
    await call('App', 'focus', this.name);
  }

  async close(): Promise<void> {
    await call('App', 'close', this.name);
  }

  async window(): Promise<any> {
    return await call('App', 'window', this.name);
  }
}

export class Pattern {
  private path: string;
  private similarity: number = 0.7;

  constructor(path: string) { this.path = path; }

  similar(value: number): Pattern {
    this.similarity = value;
    return this;
  }

  exact(): Pattern {
    this.similarity = 0.99;
    return this;
  }
}

export class VNCScreen {
  static async start(host: string, port: number, password: string,
                     width: number, height: number): Promise<VNCScreen> {
    await call('VNCScreen', 'start', host, port, password, width, height);
    return new VNCScreen();
  }

  async click(target: string): Promise<void> {
    await call('VNCScreen', 'click', target);
  }

  async type(text: string): Promise<void> {
    await call('VNCScreen', 'type', text);
  }

  async stop(): Promise<void> {
    await call('VNCScreen', 'stop');
  }
}

export { start, stop } from './gateway';
```

## 6. Exemples d'usage

### 6.1 Script basique

```javascript
const { Screen } = require('oculix');

async function main() {
  const screen = new Screen();
  await screen.click("login_button.png");
  await screen.type("admin");
  await screen.type("password123");
  await screen.click("submit.png");
  await screen.wait("dashboard.png", 10);
}

main().catch(console.error);
```

### 6.2 Avec Playwright (hybride browser + desktop)

```javascript
const { chromium } = require('playwright');
const { Screen, App } = require('oculix');

async function test() {
  // Browser part — Playwright
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto('https://myapp.com/export');
  await page.click('#export-pdf');

  // Desktop part — OculiX
  const screen = new Screen();
  await screen.wait("save_dialog.png", 10);
  await screen.type("report.pdf");
  await screen.click("save_button.png");

  // Verify file was saved
  const app = await App.open("explorer.exe");
  await screen.wait("report_pdf_icon.png", 5);

  await browser.close();
}

test();
```

### 6.3 Avec Jest

```javascript
const { Screen, App } = require('oculix');

describe('Calculator', () => {
  let app;

  beforeAll(async () => {
    app = await App.open('calc');
  });

  afterAll(async () => {
    await app.close();
  });

  test('addition', async () => {
    const screen = new Screen();
    await screen.click("button_7.png");
    await screen.click("button_plus.png");
    await screen.click("button_3.png");
    await screen.click("button_equals.png");
    expect(await screen.exists("result_10.png")).toBeTruthy();
  });
});
```

### 6.4 VNC remote

```javascript
const { VNCScreen } = require('oculix');

async function testPOS() {
  const vnc = await VNCScreen.start("10.184.10.147", 5900, "", 1920, 1080);
  await vnc.click("auchan_logo.png");
  await vnc.type("1234");
  await vnc.stop();
}

testPOS();
```

## 7. package.json

```json
{
  "name": "oculix",
  "version": "0.1.0",
  "description": "Visual automation for the real world - Node.js wrapper for OculiX",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "scripts": {
    "build": "tsc",
    "test": "jest",
    "prepublishOnly": "npm run build"
  },
  "keywords": ["visual-testing", "automation", "ocr", "sikuli", "gui-testing", "desktop-testing"],
  "author": "Julien Mer <julien.mer38@gmail.com>",
  "license": "MIT",
  "dependencies": {},
  "devDependencies": {
    "typescript": "^5.0.0",
    "@types/node": "^20.0.0",
    "jest": "^29.0.0",
    "ts-jest": "^29.0.0"
  },
  "engines": { "node": ">=18.0.0" },
  "repository": {
    "type": "git",
    "url": "https://github.com/oculix-org/operix-js"
  }
}
```

## 8. Risques et mitigations

| Risque | Mitigation |
|---|---|
| Java pas installe | Message clair au premier appel + lien download |
| Latence JSON-RPC | Acceptable pour visual testing (actions = secondes). Migrer vers node-java-bridge si necessaire |
| async/await partout | Normal en Node, pas un probleme |
| TypeScript obligatoire | Non, le package compile en JS, utilisable sans TS |
| Concurrence avec Playwright | Complementaire, pas concurrent (browser vs desktop) |

## 9. Roadmap

| Phase | Contenu | Duree |
|---|---|---|
| Phase 1 | Gateway + Screen + Pattern + App | 3 jours |
| Phase 2 | VNCScreen + ADBScreen | 1 jour |
| Phase 3 | TypeScript types + Jest examples | 1 jour |
| Phase 4 | Playwright hybrid example | 1 jour |
| Phase 5 | npm publication + README | 1 jour |

**Total : ~1 semaine**

---

*"npm install oculix. Desktop visual testing meets the Node.js ecosystem."*

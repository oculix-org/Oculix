# CDC — Operix-NET (C# / .NET)
## C# wrapper for OculiX via IKVM.NET

**Auteur :** Julien Mer — JMer Consulting
**Date :** 19 avril 2026
**Statut :** A implementer
**Repo cible :** oculix-org/operix-dotnet
**NuGet :** OculiX
**Dependance :** oculixapi (Maven Central) converti via IKVM

---

## 1. Probleme

Le monde .NET/C# est enorme en entreprise (banque, assurance, retail, sante).
Les QA .NET utilisent NUnit, xUnit, SpecFlow, MSTest. Pour le visual testing,
ils n'ont que des outils payants (Ranorex, TestComplete, UiPath) ou des
solutions browser-only (Playwright .NET).

Aucun outil open source ne fait du visual testing desktop/VNC/Android en C#.

## 2. Solution

Un package NuGet `OculiX` qui expose toute l'API OculiX en C# natif.

```bash
dotnet add package OculiX
```

```csharp
using OculiX;

var screen = new Screen();
screen.Click("button.png");
var app = App.Open("notepad");
```

## 3. Architecture — Deux approches

### Option A : IKVM.NET (recommande)

IKVM.NET convertit le bytecode Java en assemblies .NET. Le JAR OculiX
devient une DLL .NET native, appelable directement depuis C# sans JVM.

```
oculixapi.jar  --IKVM-->  OculiX.dll  --NuGet-->  dotnet add package OculiX
```

- Zero JVM a l'execution
- Appels natifs C# (pas de pont, pas de serialisation)
- Performance identique a Java
- IKVM 8.x supporte Java 17 bytecode

### Option B : Process bridge (fallback)

Si IKVM pose des problemes de compatibilite (natives OpenCV, JNI),
fallback vers un modele client-serveur comme Node.js :

```
C# client  --JSON-RPC-->  JVM process (oculixapi.jar + JSON-RPC server)
```

Plus simple mais necessite Java installe. Recommande seulement si
IKVM ne gere pas les natives OpenCV.

## 4. Structure du repo

```
oculix-org/operix-dotnet/
|-- README.md
|-- LICENSE (MIT)
|-- OculiX.sln
|-- src/
|   +-- OculiX/
|       |-- OculiX.csproj
|       |-- Screen.cs
|       |-- Region.cs
|       |-- Pattern.cs
|       |-- App.cs
|       |-- VNCScreen.cs
|       |-- ADBScreen.cs
|       |-- SSHTunnel.cs
|       |-- OCR.cs
|       |-- Key.cs
|       |-- Match.cs
|       |-- FindFailed.cs
|       +-- Settings.cs
|-- tests/
|   +-- OculiX.Tests/
|       |-- OculiX.Tests.csproj
|       |-- ScreenTests.cs
|       |-- AppTests.cs
|       +-- PatternTests.cs
+-- examples/
    |-- BasicExample/
    |-- NUnitExample/
    +-- SpecFlowExample/
```

## 5. Composants detailles

### 5.1 Screen.cs

```csharp
using IKVM.Java;
using org.sikuli.script;

namespace OculiX
{
    public class Screen
    {
        private readonly org.sikuli.script.Screen _screen;

        public Screen()
        {
            _screen = new org.sikuli.script.Screen();
        }

        public Match Click(string target)
        {
            return new Match(_screen.click(target));
        }

        public Match DoubleClick(string target)
        {
            return new Match(_screen.doubleClick(target));
        }

        public Match RightClick(string target)
        {
            return new Match(_screen.rightClick(target));
        }

        public void Type(string text)
        {
            _screen.type(text);
        }

        public Match Wait(string target, double timeout = 10)
        {
            return new Match(_screen.wait(target, timeout));
        }

        public Match Find(string target)
        {
            return new Match(_screen.find(target));
        }

        public bool Exists(string target, double timeout = 3)
        {
            return _screen.exists(target, timeout) != null;
        }

        public string Text()
        {
            return _screen.text();
        }

        public ScreenImage Capture()
        {
            return _screen.capture();
        }
    }
}
```

### 5.2 App.cs

```csharp
namespace OculiX
{
    public class App
    {
        private readonly org.sikuli.script.App _app;

        private App(org.sikuli.script.App app)
        {
            _app = app;
        }

        public static App Open(string path)
        {
            return new App(org.sikuli.script.App.open(path));
        }

        public void Focus()
        {
            _app.focus();
        }

        public Region Window()
        {
            return new Region(_app.window());
        }

        public void Close()
        {
            _app.close();
        }
    }
}
```

### 5.3 Pattern.cs

```csharp
namespace OculiX
{
    public class Pattern
    {
        private readonly org.sikuli.script.Pattern _pattern;

        public Pattern(string imagePath)
        {
            _pattern = new org.sikuli.script.Pattern(imagePath);
        }

        public Pattern Similar(float similarity)
        {
            _pattern.similar(similarity);
            return this;
        }

        public Pattern Exact()
        {
            _pattern.exact();
            return this;
        }

        public Pattern TargetOffset(int x, int y)
        {
            _pattern.targetOffset(x, y);
            return this;
        }

        internal org.sikuli.script.Pattern Native => _pattern;
    }
}
```

### 5.4 VNCScreen.cs

```csharp
namespace OculiX
{
    public class VNCScreen
    {
        private readonly org.sikuli.vnc.VNCScreen _vnc;

        private VNCScreen(org.sikuli.vnc.VNCScreen vnc)
        {
            _vnc = vnc;
        }

        public static VNCScreen Start(string host, int port, string password,
                                       int width, int height)
        {
            return new VNCScreen(
                org.sikuli.vnc.VNCScreen.start(host, port, password, width, height));
        }

        public void Click(string target)
        {
            _vnc.click(target);
        }

        public void Type(string text)
        {
            _vnc.type(text);
        }

        public void Stop()
        {
            _vnc.stop();
        }
    }
}
```

## 6. Exemples d'usage

### 6.1 Script basique

```csharp
using OculiX;

var screen = new Screen();
screen.Click("login_button.png");
screen.Type("admin");
screen.Type(Key.TAB);
screen.Type("password123");
screen.Click("submit.png");
screen.Wait("dashboard.png", 10);
```

### 6.2 Avec NUnit

```csharp
using NUnit.Framework;
using OculiX;

[TestFixture]
public class CalculatorTests
{
    private App _app;
    private Screen _screen;

    [SetUp]
    public void Setup()
    {
        _app = App.Open("calc");
        _screen = new Screen();
    }

    [TearDown]
    public void TearDown()
    {
        _app.Close();
    }

    [Test]
    public void Addition_7Plus3_Returns10()
    {
        _screen.Click("button_7.png");
        _screen.Click("button_plus.png");
        _screen.Click("button_3.png");
        _screen.Click("button_equals.png");
        Assert.IsTrue(_screen.Exists("result_10.png"));
    }
}
```

### 6.3 Avec SpecFlow (BDD)

```gherkin
Feature: POS Login
  Scenario: Cashier logs in successfully
    Given the POS application is open
    When I enter cashier code "1234"
    And I click the login button
    Then I should see the main menu
```

```csharp
[Binding]
public class POSSteps
{
    private readonly Screen _screen = new Screen();

    [Given("the POS application is open")]
    public void GivenPOSOpen()
    {
        var vnc = VNCScreen.Start("10.184.10.147", 5900, "", 1920, 1080);
    }

    [When("I enter cashier code {string}")]
    public void WhenEnterCode(string code)
    {
        _screen.Type(code);
    }

    [When("I click the login button")]
    public void WhenClickLogin()
    {
        _screen.Click("login_button.png");
    }

    [Then("I should see the main menu")]
    public void ThenSeeMainMenu()
    {
        Assert.IsTrue(_screen.Exists("main_menu.png", 10));
    }
}
```

### 6.4 VNC remote

```csharp
using OculiX;

var tunnel = new SSHTunnel("root", "10.184.10.147", 22, "password");
tunnel.Open(5900, "localhost", 5900);

var vnc = VNCScreen.Start("localhost", 5900, "", 1920, 1080);
vnc.Click("auchan_logo.png");
vnc.Type("1234");
vnc.Stop();
```

## 7. OculiX.csproj

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <PackageId>OculiX</PackageId>
    <Version>0.1.0</Version>
    <Authors>Julien Mer</Authors>
    <Description>Visual automation for the real world - .NET wrapper for OculiX</Description>
    <PackageLicenseExpression>MIT</PackageLicenseExpression>
    <PackageProjectUrl>https://github.com/oculix-org/operix-dotnet</PackageProjectUrl>
    <PackageTags>visual-testing;automation;ocr;sikuli;gui-testing;desktop-testing</PackageTags>
    <RepositoryUrl>https://github.com/oculix-org/operix-dotnet</RepositoryUrl>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="IKVM" Version="8.9.2" />
  </ItemGroup>
</Project>
```

## 8. IKVM conversion du JAR

```bash
# Convertir oculixapi.jar en DLL .NET
ikvmc -target:library -out:OculiX.Core.dll oculixapi-3.0.2.jar

# Ou via le package NuGet IKVM (automatique au build)
# Le .csproj reference IKVM et le JAR est converti au build time
```

## 9. Risques et mitigations

| Risque | Mitigation |
|---|---|
| IKVM ne gere pas les natives OpenCV | Fallback vers process bridge (JSON-RPC). Ou embarquer les natives OpenCV separement |
| IKVM Java 17 support | IKVM 8.x supporte Java 17. Verifier avec les features Java 17 utilisees |
| Taille du package NuGet | Le JAR converti + IKVM runtime = ~50-100 MB. Acceptable pour un outil de test |
| API naming convention | Java = camelCase, C# = PascalCase. Le wrapper traduit les noms |
| .NET 8 minimum | Acceptable en 2026, .NET 6 est en fin de vie |

## 10. Roadmap

| Phase | Contenu | Duree |
|---|---|---|
| Phase 1 | IKVM conversion + Screen + Pattern + App | 3 jours |
| Phase 2 | VNCScreen + ADBScreen + SSHTunnel | 2 jours |
| Phase 3 | NUnit + SpecFlow examples | 1 jour |
| Phase 4 | NuGet publication + README | 1 jour |
| Phase 5 | CI (GitHub Actions: test on Win/Mac/Linux) | 1 jour |

**Total : ~1 semaine**

## 11. Avantage IKVM vs Process Bridge

| Critere | IKVM | Process Bridge |
|---|---|---|
| JVM requise | Non | Oui |
| Performance | Native .NET | Serialisation JSON overhead |
| Distribution | Single DLL | JAR + JVM |
| Complexite | Moyenne (conversion) | Simple (child process) |
| Natives (OpenCV) | A valider | Gerees par la JVM |
| Debugging | Natif dans Visual Studio | Deux process a debugger |

---

*"dotnet add package OculiX. Visual automation meets the .NET enterprise."*

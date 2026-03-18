# testng-qatouch

A TestNG listener that automatically syncs test cases, creates test runs, and uploads results to [QA Touch](https://www.qatouch.com/) — **no TR codes required**.

## Features

- **Full sync mode** — auto-creates QA Touch modules from your `<test>` blocks, creates missing test cases, and builds test runs — all from a single `testng.xml`
- **Title-based matching** — no need to put TR codes in your test names
- **Auto-creates test runs** with milestone and assignment
- **Error details & screenshots** — failed tests upload the error message and an optional screenshot to QA Touch
- **Uploads results** (Passed / Failed / Untested) after the suite finishes
- **Thread-safe** — works with parallel test execution
- Zero extra config files — configure via environment variables or system properties

## Installation

Add the dependency to your project:

```xml
<dependency>
  <groupId>io.github.ujjwala91</groupId>
  <artifactId>testng-qatouch</artifactId>
  <version>1.1.0</version>
</dependency>
```

## Usage

### Option 1: testng.xml

```xml
<suite name="My Suite">
  <listeners>
    <listener class-name="io.github.ujjwala91.QATouchListener"/>
  </listeners>
  <test name="Login Module">
    <classes>
      <class name="com.example.LoginTests"/>
    </classes>
  </test>
  <test name="Dashboard Module">
    <classes>
      <class name="com.example.DashboardTests"/>
    </classes>
  </test>
</suite>
```

When `QATOUCH_SYNC=true` (the default), each `<test>` block name becomes a QA Touch module automatically.

### Option 2: Programmatic

```java
import io.github.ujjwala91.QATouchListener;

TestNG tng = new TestNG();
tng.addListener(new QATouchListener());
tng.setTestSuites(List.of("testng.xml"));
tng.run();
```

### Option 3: @Listeners annotation

```java
import io.github.ujjwala91.QATouchListener;

@Listeners(QATouchListener.class)
public class MyTests {
    @Test(description = "should display the home page title")
    public void testHomePageTitle() {
        // ...
    }
}
```

## Configuration

Set these as **environment variables** or **Java system properties** (`-D`):

| Environment Variable      | System Property         | Required                         | Default             | Description                                        |
| ------------------------- | ----------------------- | -------------------------------- | ------------------- | -------------------------------------------------- |
| `QATOUCH_DOMAIN`          | `qatouch.domain`        | Yes                              | —                   | Your QA Touch domain (subdomain)                   |
| `QATOUCH_API_TOKEN`       | `qatouch.apiToken`      | Yes                              | —                   | QA Touch API token                                 |
| `QATOUCH_PROJECT_KEY`     | `qatouch.projectKey`    | Yes                              | —                   | QA Touch project key                               |
| `QATOUCH_TESTSUITE_ID`    | `qatouch.testsuiteId`   | Yes (unless `QATOUCH_SYNC=true`) | —                   | Module/testsuite key to push results into          |
| `QATOUCH_ASSIGN_TO`       | `qatouch.assignTo`      | Yes                              | —                   | User key to assign the test run to                 |
| `QATOUCH_SYNC`            | `qatouch.sync`          | No                               | `true`              | Full sync: auto-create modules & cases from suites |
| `QATOUCH_CREATE_CASES`    | `qatouch.createCases`   | No                               | `true`              | Auto-create missing test cases                     |
| `QATOUCH_CREATE_TEST_RUN` | `qatouch.createTestRun` | No                               | `true`              | Auto-create a test run                             |
| `QATOUCH_MILESTONE_NAME`  | `qatouch.milestoneName` | No                               | `TestNG Automation` | Milestone name (reuses or creates)                 |
| `QATOUCH_MILESTONE_KEY`   | `qatouch.milestoneKey`  | No                               | —                   | Existing milestone key (skips lookup)              |
| `QATOUCH_TAG`             | `qatouch.tag`           | No                               | `testng`            | Tag applied to the test run                        |

### Sync mode vs. fixed module

| Mode               | Config                                           | Behaviour                                                                                                         |
| ------------------ | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| **Sync** (default) | `QATOUCH_SYNC=true`, no `QATOUCH_TESTSUITE_ID`   | Reads `<test>` names from `testng.xml`, matches or creates QA Touch modules, creates missing cases in each module |
| **Fixed module**   | `QATOUCH_SYNC=false`, set `QATOUCH_TESTSUITE_ID` | All tests are pushed into the single specified module — no modules are created                                    |

### Example: Full sync (recommended)

```bash
export QATOUCH_DOMAIN=your-domain
export QATOUCH_API_TOKEN=your-token
export QATOUCH_PROJECT_KEY=YOUR_PROJECT_KEY
export QATOUCH_ASSIGN_TO=YOUR_USER_KEY
# QATOUCH_SYNC defaults to true — modules are created from <test> names
mvn test
```

### Example: Fixed module

```bash
export QATOUCH_DOMAIN=your-domain
export QATOUCH_API_TOKEN=your-token
export QATOUCH_PROJECT_KEY=YOUR_PROJECT_KEY
export QATOUCH_TESTSUITE_ID=YOUR_TESTSUITE_ID
export QATOUCH_ASSIGN_TO=YOUR_USER_KEY
export QATOUCH_SYNC=false
mvn test
```

## Error Details & Screenshots

When a test fails, the listener automatically captures:

- **Error message** — exception class, message, and the first 5 stack frames
- **Screenshot** — if your test stores a screenshot path as a TestNG attribute

To attach a screenshot, set the `screenshot` attribute in your test:

```java
@Test
public void testLogin() {
    try {
        // ... test logic
    } catch (Exception e) {
        String path = takeScreenshot(); // your screenshot method
        Reporter.getCurrentTestResult().setAttribute("screenshot", path);
        throw e;
    }
}
```

The error message and screenshot are uploaded to QA Touch as a comment on the failed test result.

## Test Name Matching

The listener matches TestNG tests to QA Touch cases by title:

1. If `@Test(description = "...")` is set, the **description** is used
2. Otherwise, the **method name** is used

Titles are normalized (lowercased, special characters removed) for fuzzy matching.

## Status Mapping

| TestNG Result          | QA Touch Status |
| ---------------------- | --------------- |
| Passed                 | Passed (1)      |
| Failed                 | Failed (5)      |
| Timed out              | Failed (5)      |
| Skipped                | Untested (2)    |
| Failed within success% | Passed (1)      |

## CI/CD (GitHub Actions)

```yaml
- name: Run TestNG tests
  run: mvn test
  env:
    QATOUCH_DOMAIN: your-domain
    QATOUCH_API_TOKEN: ${{ secrets.QATOUCH_API_TOKEN }}
    QATOUCH_PROJECT_KEY: ${{ secrets.QATOUCH_PROJECT_KEY }}
    QATOUCH_ASSIGN_TO: ${{ secrets.QATOUCH_ASSIGN_TO }}
```

Set `QATOUCH_SYNC=false` and add `QATOUCH_TESTSUITE_ID` if you want to target a fixed module instead of auto-creating from suite names.

## How It Works

1. **`onStart(ISuite)`** — Resolves modules (sync mode) or loads existing cases (fixed module), creates missing test cases, creates a test run
2. **`onTestSuccess/onTestFailure/onTestSkipped`** — Collects each test result (failures also capture error message and screenshot path)
3. **`onFinish(ISuite)`** — Uploads failed tests individually with error details/screenshots, then bulk-uploads the rest

## Building

```bash
mvn clean package
```

## License

ISC

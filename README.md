# testng-qatouch

A TestNG listener that automatically syncs test cases, creates test runs, and uploads results to [QA Touch](https://www.qatouch.com/) — **no TR codes required**.

## Features

- **Auto-creates test cases** in QA Touch from your TestNG test method names or descriptions
- **Title-based matching** — no need to put TR codes in your test names
- **Auto-creates test runs** with milestone and assignment
- **Uploads results** (Passed / Failed / Untested) after the suite finishes
- **Thread-safe** — works with parallel test execution
- Zero extra config files — configure via environment variables or system properties

## Installation

Add the JAR to your project, or install with Maven:

```xml
<dependency>
  <groupId>io.github.ujjwala91</groupId>
    <artifactId>testng-qatouch</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Option 1: testng.xml

```xml
<suite name="My Suite">
  <listeners>
    <listener class-name="io.github.ujjwala91.QATouchListener"/>
  </listeners>
  <test name="My Tests">
    <classes>
      <class name="com.example.MyTests"/>
    </classes>
  </test>
</suite>
```

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

| Environment Variable      | System Property         | Required | Default             | Description                           |
| ------------------------- | ----------------------- | -------- | ------------------- | ------------------------------------- |
| `QATOUCH_DOMAIN`          | `qatouch.domain`        | Yes      | —                   | Your QA Touch domain (subdomain)      |
| `QATOUCH_API_TOKEN`       | `qatouch.apiToken`      | Yes      | —                   | QA Touch API token                    |
| `QATOUCH_PROJECT_KEY`     | `qatouch.projectKey`    | Yes      | —                   | QA Touch project key                  |
| `QATOUCH_TESTSUITE_ID`    | `qatouch.testsuiteId`   | Yes      | —                   | Module/testsuite key                  |
| `QATOUCH_ASSIGN_TO`       | `qatouch.assignTo`      | Yes      | —                   | User key to assign the test run to    |
| `QATOUCH_MILESTONE_NAME`  | `qatouch.milestoneName` | No       | `TestNG Automation` | Milestone name (reuses or creates)    |
| `QATOUCH_MILESTONE_KEY`   | `qatouch.milestoneKey`  | No       | —                   | Existing milestone key (skips lookup) |
| `QATOUCH_CREATE_CASES`    | `qatouch.createCases`   | No       | `true`              | Auto-create missing test cases        |
| `QATOUCH_CREATE_TEST_RUN` | `qatouch.createTestRun` | No       | `true`              | Auto-create a test run                |
| `QATOUCH_TAG`             | `qatouch.tag`           | No       | `testng`            | Tag applied to the test run           |

### Example: Running with system properties

```bash
mvn test \
  -Dqatouch.domain=xrite \
  -Dqatouch.apiToken=your-token \
  -Dqatouch.projectKey=Xb3Z \
  -Dqatouch.testsuiteId=q7pg7 \
  -Dqatouch.assignTo=1NdK
```

### Example: Running with environment variables

```bash
export QATOUCH_DOMAIN=xrite
export QATOUCH_API_TOKEN=your-token
export QATOUCH_PROJECT_KEY=Xb3Z
export QATOUCH_TESTSUITE_ID=q7pg7
export QATOUCH_ASSIGN_TO=1NdK
mvn test
```

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
    QATOUCH_DOMAIN: xrite
    QATOUCH_API_TOKEN: ${{ secrets.QATOUCH_API_TOKEN }}
    QATOUCH_PROJECT_KEY: Xb3Z
    QATOUCH_TESTSUITE_ID: q7pg7
    QATOUCH_ASSIGN_TO: 1NdK
```

## How It Works

1. **`onStart(ISuite)`** — Fetches existing QA Touch cases, creates missing ones, creates a test run
2. **`onTestSuccess/onTestFailure/onTestSkipped`** — Collects each test result
3. **`onFinish(ISuite)`** — Uploads all results in a single API call

## Building

```bash
mvn clean package
```

## Smoke Test As A Dependency

This repository includes a tiny consumer project at `consumer-smoke-test/` that
uses the published coordinates as a normal dependency and runs a basic TestNG
suite.

```bash
cd consumer-smoke-test
mvn test
```

Without QA Touch environment variables, the listener disables itself and the
test still passes. That is enough to verify dependency resolution, listener
loading, and TestNG integration.

## License

ISC

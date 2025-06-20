/*
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.playwright;

import com.microsoft.playwright.options.Contrast;
import com.microsoft.playwright.options.Geolocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.microsoft.playwright.options.ColorScheme.DARK;
import static com.microsoft.playwright.Utils.mapOf;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

public class TestDefaultBrowserContext2 extends TestBase {


  private BrowserContext persistentContext;
  @TempDir Path tempDir;

  @AfterEach
  private void closePersistentContext() {
    if (persistentContext != null) {
      persistentContext.close();
      persistentContext = null;
    }
  }

  private Page launchPersistent() {
    return launchPersistent(null);
  }

  private Page launchPersistent(BrowserType.LaunchPersistentContextOptions options) {
    Path userDataDir = tempDir.resolve("user-data-dir");
    assertNull(persistentContext);
    persistentContext = browserType.launchPersistentContext(userDataDir, options);
    return persistentContext.pages().get(0);
  }

  @Test
  void shouldSupportHasTouchOption() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions().setHasTouch(true));
    page.navigate(server.PREFIX + "/mobile.html");
    assertEquals(true, page.evaluate("() => 'ontouchstart' in window"));
  }

  @Test
  @DisabledIf(value="com.microsoft.playwright.TestBase#isFirefox", disabledReason="skip")
  void shouldWorkInPersistentContext() {
    // Firefox does not support mobile.
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions()
      .setViewportSize(320, 480).setIsMobile(true));
    page.navigate(server.PREFIX + "/empty.html");
    assertEquals(980, page.evaluate("() => window.innerWidth"));
  }

  @Test
  void shouldSupportColorSchemeOption() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions().setColorScheme(DARK));
    assertEquals(false, page.evaluate("matchMedia('(prefers-color-scheme: light)').matches"));
    assertEquals(true, page.evaluate("matchMedia('(prefers-color-scheme: dark)').matches"));
  }

  @Test
  void shouldSupportTimezoneIdOption() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions()
      .setLocale("en-US").setTimezoneId("America/Jamaica"));
    assertEquals("Sat Nov 19 2016 13:12:34 GMT-0500 (Eastern Standard Time)",
      page.evaluate("new Date(1479579154987).toString()"));
  }

  @Test
  void shouldSupportLocaleOption() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions()
      .setLocale("fr-FR"));
    assertEquals("fr-FR", page.evaluate("navigator.language"));
  }

  @Test
  void shouldSupportGeolocationAndPermissionsOptions() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions()
      .setGeolocation(new Geolocation(10, 10))
      .setPermissions(asList("geolocation")));
    page.navigate(server.EMPTY_PAGE);
    Object geolocation = page.evaluate("() => new Promise(resolve => navigator.geolocation.getCurrentPosition(position => {\n" +
      "  resolve({latitude: position.coords.latitude, longitude: position.coords.longitude});\n" +
      "}))");
    assertEquals(mapOf("latitude", 10, "longitude", 10), geolocation);
  }

  //  @Test
  void shouldSupportIgnoreHTTPSErrorsOption() {
    // TODO: net::ERR_EMPTY_RESPONSE at https://localhost:8908/empty.html
//    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions().setIgnoreHTTPSErrors(true));
//    Response response = page.navigate(httpsServer.EMPTY_PAGE);
//    assertTrue(response.ok());
  }

  @Test
  void shouldSupportExtraHTTPHeadersOption() throws ExecutionException, InterruptedException {
//   TODO: test.flaky(browserName === "firefox" && headful && platform === "linux", "Intermittent timeout on bots");
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions().setExtraHTTPHeaders(mapOf("foo", "bar")));
    Future<Server.Request> request = server.futureRequest("/empty.html");
    page.navigate(server.EMPTY_PAGE);
    assertEquals(asList("bar"), request.get().headers.get("foo"));
  }

  @Test
  void shouldAcceptUserDataDir() throws IOException {
// TODO:   test.flaky(browserName === "chromium");
    Path userDataDir = tempDir.resolve("user-data-dir");
    BrowserContext context = browserType.launchPersistentContext(userDataDir);
    assertTrue(userDataDir.toFile().listFiles().length > 0);
    context.close();
    assertTrue(userDataDir.toFile().listFiles().length > 0);
  }

  @Test
  void shouldRestoreStateFromUserDataDir() throws IOException {
//  TODO:  test.slow();
    Path userDataDir = tempDir.resolve("user-data-dir");
    BrowserType.LaunchPersistentContextOptions browserOptions = null;
    BrowserContext browserContext = browserType.launchPersistentContext(userDataDir, browserOptions);
    Page page = browserContext.newPage();
    page.navigate(server.EMPTY_PAGE);
    page.evaluate("() => localStorage.hey = 'hello'");
    browserContext.close();

    BrowserContext browserContext2 = browserType.launchPersistentContext(userDataDir, browserOptions);
    Page page2 = browserContext2.newPage();
    page2.navigate(server.EMPTY_PAGE);
    assertEquals("hello", page2.evaluate("localStorage.hey"));
    browserContext2.close();

    Path userDataDir2 = tempDir.resolve("user-data-dir-2");
    BrowserContext browserContext3 = browserType.launchPersistentContext(userDataDir2, browserOptions);
    Page page3 = browserContext3.newPage();
    page3.navigate(server.EMPTY_PAGE);
    assertNotEquals("hello", page3.evaluate("localStorage.hey"));
    browserContext3.close();
  }

  @Test
  void shouldHaveDefaultURLWhenLaunchingBrowser() {
    launchPersistent();
    List<String> urls = persistentContext.pages().stream().map(page -> page.url()).collect(Collectors.toList());
    assertEquals(asList("about:blank"), urls);
  }

  @Test
  @DisabledIf(value="com.microsoft.playwright.TestBase#isFirefox", disabledReason="skip")
  void shouldThrowIfPageArgumentIsPassed() throws IOException {
    BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
      .setArgs(asList(server.EMPTY_PAGE));
    Path userDataDir = tempDir.resolve("user-data-dir");
    PlaywrightException e = assertThrows(PlaywrightException.class, () -> {
      browserType.launchPersistentContext(userDataDir, options);
    });
    assertTrue(e.getMessage().contains("can not specify page"));
  }

  @Test
  void shouldWorkWithIgnoreDefaultArgs() {
    // Ignore arguments by name.
    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setIgnoreDefaultArgs(asList("foo"));
    Browser browser = browserType.launch(options);
    Page page = browser.newPage();
    browser.close();
    // Check that there is a way to ignore all arguments.
    new BrowserType.LaunchOptions().setIgnoreAllDefaultArgs(true);
  }

  void shouldHavePassedURLWhenLaunchingWithIgnoreDefaultArgsTrue() {
  }

  void shouldHandleTimeout() {
  }

  void shouldHandleException() {
  }

  @Test
  void shouldFireCloseEventForAPersistentContext() {
    launchPersistent();
    boolean[] closed = {false};
    persistentContext.onClose(context -> closed[0] = true);
    closePersistentContext();
    assertTrue(closed[0]);
  }

  void coverageShouldWork() {
    // TODO:
  }

  void coverageShouldBeMissing() {
    // TODO:
  }

  @Test
  void shouldRespectSelectors() {
    Page page = launchPersistent();
    String defaultContextCSS = "{\n" +
      "  create(root, target) {},\n" +
      "  query(root, selector) {\n" +
      "    return root.querySelector(selector);\n" +
      "  },\n" +
      "  queryAll(root, selector) {\n" +
      "    return Array.from(root.querySelectorAll(selector));\n" +
      "  }\n" +
      "}";
    playwright.selectors().register("defaultContextCSS", defaultContextCSS);

    page.setContent("<div>hello</div>");
    assertEquals("hello", page.innerHTML("css=div"));
    assertEquals("hello", page.innerHTML("defaultContextCSS=div"));
  }

  @Test
  void shouldUploadLargeFile(@TempDir Path tmpDir) throws IOException, ExecutionException, InterruptedException {
    Assumptions.assumeTrue(3 <= (Runtime.getRuntime().maxMemory() >> 30), "Fails if max heap size is < 3Gb");
    Page page = launchPersistent();
    page.navigate(server.PREFIX + "/input/fileupload.html");
    Path uploadFile = tmpDir.resolve("200MB.zip");
    String str = String.join("", Collections.nCopies(4 * 1024, "A"));

    try (Writer stream = new OutputStreamWriter(Files.newOutputStream(uploadFile))) {
      for (int i = 0; i < 50 * 1024; i++) {
        stream.write(str);
      }
    }
    Locator input = page.locator("input[type='file']");
    JSHandle events = input.evaluateHandle("e => {\n" +
      "    const events = [];\n" +
      "    e.addEventListener('input', () => events.push('input'));\n" +
      "    e.addEventListener('change', () => events.push('change'));\n" +
      "    return events;\n" +
      "  }");
    input.setInputFiles(uploadFile);
    assertEquals("200MB.zip", input.evaluate("e => e.files[0].name"));
    assertEquals(asList("input", "change"), events.evaluate("e => e"));
    CompletableFuture<MultipartFormData> formData = new CompletableFuture<>();
    server.setRoute("/upload", exchange -> {
      try {
        MultipartFormData multipartFormData = MultipartFormData.parseRequest(exchange);
        formData.complete(multipartFormData);
      } catch (Exception e) {
        e.printStackTrace();
        formData.completeExceptionally(e);
      }
      exchange.sendResponseHeaders(200, -1);
    });
    page.click("input[type=submit]", new Page.ClickOptions().setTimeout(90_000));
    List<MultipartFormData.Field> fields = formData.get().fields;
    assertEquals(1, fields.size());
    assertEquals("200MB.zip", fields.get(0).filename);
    assertEquals(200 * 1024 * 1024, fields.get(0).content.length());
  }

  @Test
  void shouldSupportContrastOption() {
    Page page = launchPersistent(new BrowserType.LaunchPersistentContextOptions().setContrast(Contrast.MORE));
    assertEquals(true, page.evaluate("() => matchMedia('(prefers-contrast: more)').matches"));
    assertEquals(false, page.evaluate("() => matchMedia('(prefers-contrast: no-preference)').matches"));
  }

  static boolean tempDirCanBeOnDifferentRoot() {
    return isWindows;
  }

  @Test
  @DisabledIf(value="tempDirCanBeOnDifferentRoot", disabledReason="IllegalArgument 'other' has different root on GitHub Actions.")
  void shouldAcceptRelativeUserDataDir(@TempDir Path tmpDir) throws Exception {
    Path userDataDir = tempDir.resolve("user-data-dir");
    Path cwd = Paths.get("").toAbsolutePath();
    Path relativePath = cwd.relativize(userDataDir);
    BrowserContext context = browserType.launchPersistentContext(relativePath);
    assertTrue(Files.list(userDataDir).count() > 0);
    context.close();
  }

  @Test
  void shouldExposeBrowser() {
    Page page = launchPersistent();
    BrowserContext context = page.context();
    Browser browser = context.browser();
    assertFalse(browser.version().isEmpty());
    Page page2 = browser.newPage();
    page2.navigate("data:text/html,<html><title>Title</title></html>");
    assertEquals("Title", page2.title());
    browser.close();
    assertEquals(0, context.pages().size());
    // Next line should not throw.
    context.close();
  }
}

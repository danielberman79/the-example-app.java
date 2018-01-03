package com.contentful.tea.java;

import com.contentful.java.cda.CDAClient;
import com.contentful.java.cda.CDAEntry;
import com.contentful.tea.java.html.JadeHtmlGenerator;
import com.contentful.tea.java.models.Settings;
import com.contentful.tea.java.models.errors.ErrorParameter;
import com.contentful.tea.java.models.landing.LandingPageParameter;
import com.contentful.tea.java.services.StaticContentSetter;
import com.contentful.tea.java.services.modelconverter.EntryToLandingPage;
import com.contentful.tea.java.services.modelconverter.ExceptionToErrorParameter;
import com.contentful.tea.java.services.url.CookieParser;
import com.contentful.tea.java.services.url.UrlParameterParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import static java.lang.String.format;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

@ComponentScan
@Controller
@EnableAutoConfiguration
public class MainController {
  private static final String ERROR_PATH = "/error";

  public static void main(String[] args) {
    SpringApplication.run(MainController.class, args);
  }

  @Autowired
  @SuppressWarnings("unused")
  private Settings settings;

  @Autowired
  @SuppressWarnings("unused")
  private CookieParser cookieParser;

  @Autowired
  @SuppressWarnings("unused")
  private UrlParameterParser urlParameterParser;

  @Autowired
  @SuppressWarnings("unused")
  private StaticContentSetter staticContentSetter;

  @Autowired
  @SuppressWarnings("unused")
  private EntryToLandingPage entryToLandingPage;

  @Autowired
  @SuppressWarnings("unused")
  private ExceptionToErrorParameter exceptionToError;

  @Autowired
  @SuppressWarnings("unused")
  private JadeHtmlGenerator htmlGenerator;

  @RequestMapping("/")
  @ResponseBody
  public String home(
      @CookieValue(name = "api", defaultValue = "") String api,
      @CookieValue(name = "space_id", defaultValue = "") String spaceId,
      @CookieValue(name = "delivery_token", defaultValue = "") String deliveryToken,
      @CookieValue(name = "preview_token", defaultValue = "") String previewToken,
      @RequestParam Map<String, String> urlParameter
  ) {
    settings.loadDefaults();
    cookieParser.loadCookies(api, spaceId, deliveryToken, previewToken);
    urlParameterParser.parseUrlParameter(urlParameter);

    final CDAClient client = settings.getCurrentClient();
    final CDAEntry cdaLanding = client.fetch(CDAEntry.class).include(5).one("2uNOpLMJioKeoMq8W44uYc");
    final LandingPageParameter parameter = entryToLandingPage.convert(cdaLanding);

    staticContentSetter.applyBaseContent(parameter.getBase());

    try {
      return htmlGenerator.generate("templates/landingPage.jade", parameter.toMap());
    } catch (Throwable t) {
      throw new IllegalStateException("Cannot render landing page.", t);
    } finally {
      cookieParser.saveCookies();
    }
  }

  @RequestMapping("/courses")
  @ResponseBody
  public String courses() {
    return "courses";
  }

  @RequestMapping("/course")
  @ResponseBody
  public String course() {
    return "course";
  }

  @RequestMapping("/settings")
  @ResponseBody
  public String settings() {
    return "settings";
  }

  @ExceptionHandler(Throwable.class)
  @ResponseBody
  public String serverError(HttpServletRequest request, Exception serverException) {
    final ErrorParameter errorParameter = exceptionToError.convert(serverException);

    try {
      return htmlGenerator.generate("templates/error.jade", errorParameter.toMap());
    } catch (Exception nestedException) {
      return format(
          "<h1>Nested exception thrown while handling a server exception</h1><br/>\n\n%s while %s<br/>\n\n<!--\n%s\n\nwhile\n\n%s\n-->",
          nestedException,
          serverException,
          getStackTrace(nestedException),
          getStackTrace(serverException));
    }
  }
}
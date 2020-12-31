/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A fail-safe matcher catches exceptions that are thrown by a delegate matcher and returns an
 * alternative value.
 *
 * <p>Logs exception if it was thrown.
 *
 * @param <T> The type of the matched entity.
 * @see net.bytebuddy.matcher.FailSafeMatcher
 */
class LoggingFailSafeMatcher<T> extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Logger log = LoggerFactory.getLogger(LoggingFailSafeMatcher.class);

  /** The delegate matcher that might throw an exception. */
  private final ElementMatcher<? super T> matcher;

  /** The fallback value in case of an exception. */
  private final boolean fallback;

  /** The text description to log if exception happens. */
  private final String description;

  /**
   * Creates a new fail-safe element matcher.
   *
   * @param matcher The delegate matcher that might throw an exception.
   * @param fallback The fallback value in case of an exception.
   * @param description Descriptive string to log along with exception.
   */
  public LoggingFailSafeMatcher(
      ElementMatcher<? super T> matcher, boolean fallback, String description) {
    this.matcher = matcher;
    this.fallback = fallback;
    this.description = description;
  }

  @Override
  public boolean matches(T target) {
    try {
      return matcher.matches(target);
    } catch (Exception e) {
      log.debug(description, e);
      return fallback;
    }
  }

  @Override
  public String toString() {
    return "failSafe(try(" + matcher + ") or " + fallback + ")";
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (getClass() != other.getClass()) {
      return false;
    } else if (fallback != ((LoggingFailSafeMatcher) other).fallback) {
      return false;
    } else {
      return matcher.equals(((LoggingFailSafeMatcher) other).matcher);
    }
  }

  @Override
  public int hashCode() {
    return (17 * 31 + matcher.hashCode()) * 31 + (fallback ? 1231 : 1237);
  }
}

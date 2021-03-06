/*
 * Copyright 2012 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.gradle.versions.updates

import groovy.transform.TupleConstructor

import static com.github.benmanes.gradle.versions.updates.DependencyUpdates.keyOf

/**
 * A reporter for the dependency updates results.
 *
 * @author Ben Manes (ben.manes@gmail.com)
 */
@TupleConstructor
class DependencyUpdatesReporter {
  /** The project evaluated against. */
  def project
  /** The revision strategy evaluated with. */
  def revision

  /** The current versions of each dependency declared in the project(s). */
  def currentVersions
  /** The latest versions of each dependency (as scoped by the revision level). */
  def latestVersions

  /** The dependencies that are up to date (same as latest found). */
  def upToDateVersions
  /** The dependencies that exceed the latest found (e.g. may not want SNAPSHOTs). */
  def downgradeVersions
  /** The dependencies where upgrades were found (below latest found). */
  def upgradeVersions
  /** The dependencies that could not be resolved. */
  def unresolved

  private static Object mutex = new Object();

  /** Writes the report to the console. */
  def writeToConsole() {
    writeTo(System.out)
  }

  /** Writes the report to the file. */
  def writeToFile(fileName) {
    def printStream = new PrintStream(fileName)
    try {
      writeTo(printStream)
    } finally {
      printStream.close()
    }
  }

  /** Writes the report to the print stream. The stream is not automatically closed. */
  def writeTo(printStream) {
    synchronized (mutex) {
      writeHeader(printStream)
      writeUpToDate(printStream)
      writeExceedLatestFound(printStream)
      writeUpgrades(printStream)
      writeUnresolved(printStream)
    }
  }

  private def writeHeader(printStream) {
    printStream.println """
      |------------------------------------------------------------
      |${project.path} Project Dependency Updates
      |------------------------------------------------------------""".stripMargin()
  }

  private def writeUpToDate(printStream) {
    if (upToDateVersions.isEmpty()) {
      printStream.println "\nAll dependencies have later versions."
    } else {
      printStream.println(
        "\nThe following dependencies are using the latest ${revision} version:")
      upToDateVersions
        .sort { a, b -> compareKeys(a.key, b.key) }
        .each { printStream.println " - ${label(it.key)}:${it.value}" }
    }
  }

  private def writeExceedLatestFound(printStream) {
    if (!downgradeVersions.isEmpty()) {
      printStream.println("\nThe following dependencies exceed the version found at the "
        + revision + " revision level:")
      downgradeVersions
        .sort { a, b -> compareKeys(a.key, b.key) }
        .each { key, version ->
          def currentVersion = currentVersions[key]
          printStream.println " - ${label(key)} [${currentVersion} <- ${version}]"
        }
    }
  }

  private def writeUpgrades(printStream) {
    if (upgradeVersions.isEmpty()) {
      printStream.println "\nAll dependencies are using the latest ${revision} versions."
    } else {
      printStream.println "\nThe following dependencies have later ${revision} versions:"
      upgradeVersions
        .sort { a, b -> compareKeys(a.key, b.key) }
        .each { key, version ->
          def currentVersion = currentVersions[key]
          printStream.println " - ${label(key)} [${currentVersion} -> ${version}]"
        }
    }
  }

  private def writeUnresolved(printStream) {
    if (!unresolved.isEmpty()) {
      printStream.println(
        "\nFailed to determine the latest version for the following dependencies "
        + "(use --info for details):")
      unresolved
        .sort { a, b -> compareKeys(keyOf(a.selector), keyOf(b.selector)) }
        .each {
          printStream.println " - " + label(keyOf(it.selector))
          project.logger.info "The exception that is the cause of unresolved state:", it.problem
        }
    }
  }

  /** Compares the dependency keys. */
  private def compareKeys(a, b) {
    (a['group'] == b['group']) ? a['name'] <=> b['name'] : a['group'] <=> b['group']
  }

  /** Returns the dependency key as a stringified label. */
  private def label(key) { key.group + ':' + key.name }
}

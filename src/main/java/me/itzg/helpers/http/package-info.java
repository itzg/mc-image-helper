/**
 * This package provides wrappers of <a href="https://projectreactor.io/docs/netty/release/reference/index.html">Reactor Netty</a>
 * to simplify common retrieval patterns such as
 * <ul>
 *     <li>parsing response JSON into Java objects</li>
 *     <li>retrieving a file into a known output name with handling of up to date checks</li>
 *     <li>retrieving a file without prior knowledge of the resulting filename</li>
 * </ul>
 * Examples:
 * <pre>{@code
 * Fetch.fetch(URI.create(...))
 *   .toDirectory(dest)
 *   .skipUpToDate(true)
 *   .assemble()
 *   .block()
 * }</pre>
 */
package me.itzg.helpers.http;
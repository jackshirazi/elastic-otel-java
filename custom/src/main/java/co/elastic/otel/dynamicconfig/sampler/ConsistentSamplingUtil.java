/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.otel.dynamicconfig.sampler;

public final class ConsistentSamplingUtil {

  private static final int RANDOM_VALUE_BITS = 56;
  private static final long MAX_THRESHOLD =
      1L << RANDOM_VALUE_BITS; // corresponds to 0% sampling probability
  private static final long MIN_THRESHOLD = 0; // corresponds to 100% sampling probability
  private static final long MAX_RANDOM_VALUE = MAX_THRESHOLD - 1;
  private static final long INVALID_THRESHOLD = -1;
  private static final long INVALID_RANDOM_VALUE = -1;

  private ConsistentSamplingUtil() {}

  /**
   * Returns for a given threshold the corresponding sampling probability.
   *
   * <p>The returned value does not always exactly match the applied sampling probability, since
   * some least significant binary digits may not be represented by double-precision floating point
   * numbers.
   *
   * @param threshold the threshold
   * @return the sampling probability
   */
  public static double calculateSamplingProbability(long threshold) {
    checkThreshold(threshold);
    return (MAX_THRESHOLD - threshold) * 0x1p-56;
  }

  /**
   * Returns the closest sampling threshold that can be used to realize sampling with the given
   * probability.
   *
   * @param samplingProbability the sampling probability
   * @return the threshold
   */
  public static long calculateThreshold(double samplingProbability) {
    checkProbability(samplingProbability);
    return MAX_THRESHOLD - Math.round(samplingProbability * 0x1p56);
  }

  /**
   * Calculates the adjusted count from a given threshold.
   *
   * @param threshold the threshold
   * @return the adjusted count
   */
  public static double calculateAdjustedCount(long threshold) {
    checkThreshold(threshold);
    return 0x1p56 / (MAX_THRESHOLD - threshold);
  }

  /**
   * Returns an invalid random value.
   *
   * <p>{@code isValidRandomValue(getInvalidRandomValue())} will always return true.
   *
   * @return an invalid random value
   */
  public static long getInvalidRandomValue() {
    return INVALID_RANDOM_VALUE;
  }

  /**
   * Returns an invalid threshold.
   *
   * <p>{@code isValidThreshold(getInvalidThreshold())} will always return true.
   *
   * @return an invalid threshold value
   */
  public static long getInvalidThreshold() {
    return INVALID_THRESHOLD;
  }

  public static long getMaxRandomValue() {
    return MAX_RANDOM_VALUE;
  }

  public static long getMinThreshold() {
    return MIN_THRESHOLD;
  }

  public static long getMaxThreshold() {
    return MAX_THRESHOLD;
  }

  public static boolean isValidRandomValue(long randomValue) {
    return 0 <= randomValue && randomValue <= getMaxRandomValue();
  }

  public static boolean isValidThreshold(long threshold) {
    return getMinThreshold() <= threshold && threshold <= getMaxThreshold();
  }

  public static boolean isValidProbability(double probability) {
    return 0 <= probability && probability <= 1;
  }

  static void checkThreshold(long threshold) {
    if (!isValidThreshold(threshold)) {
      throw new IllegalArgumentException("The threshold must be in the range [0,2^56]!");
    }
  }

  static void checkProbability(double probability) {
    if (!isValidProbability(probability)) {
      throw new IllegalArgumentException("The probability must be in the range [0,1]!");
    }
  }

  static final char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  static StringBuilder appendLast56BitHexEncoded(StringBuilder sb, long l) {
    return appendLast56BitHexEncodedHelper(sb, l, 0);
  }

  static StringBuilder appendLast56BitHexEncodedWithoutTrailingZeros(StringBuilder sb, long l) {
    int numTrailingBits = Long.numberOfTrailingZeros(l | 0x80000000000000L);
    return appendLast56BitHexEncodedHelper(sb, l, numTrailingBits);
  }

  private static StringBuilder appendLast56BitHexEncodedHelper(
      StringBuilder sb, long l, int numTrailingZeroBits) {
    for (int i = 52; i >= numTrailingZeroBits - 3; i -= 4) {
      sb.append(HEX_DIGITS[(int) ((l >>> i) & 0xf)]);
    }
    return sb;
  }
}

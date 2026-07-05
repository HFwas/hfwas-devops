package com.hfwas.devops.common.error;

/**
 * Business error code contract.
 * <p>
 * Code ranges:
 * <ul>
 *   <li>0 – success</li>
 *   <li>10000–10999 – common</li>
 *   <li>11000–11999 – auth &amp; session</li>
 *   <li>12000–12999 – user</li>
 *   <li>13000–13999 – tenant</li>
 *   <li>14000–14999 – session admin</li>
 *   <li>15000–15999 – message &amp; notify</li>
 *   <li>16000–16999 – integration</li>
 *   <li>20000–20999 – project</li>
 *   <li>21000–21999 – work item</li>
 *   <li>22000–22999 – field &amp; scheme</li>
 *   <li>23000–23999 – module</li>
 * </ul>
 */
public interface ErrorCode {

    int getCode();

    String getMessage();
}

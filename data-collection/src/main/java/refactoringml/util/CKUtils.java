package refactoringml.util;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import refactoringml.ProcessMetricsCollector;
import refactoringml.db.ClassMetric;
import refactoringml.db.MethodMetric;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static refactoringml.util.PropertiesUtils.getProperty;
import static refactoringml.util.RefactoringUtils.cleanMethodName;

public class CKUtils {
	private static final Logger log = LogManager.getLogger(ProcessMetricsCollector.class);
	private static long timeout = Long.parseLong(getProperty("timeoutRefactoringMiner"));

	//TODO: figure out if we could parallelize the CK tool for various class files on the same commit
	//Calls the CK.calculate with a timeout.
	public static void calculate(String tempdir, String commitHash, String projectUrl, CKNotifier ckNotifier){
		ExecutorService executor = Executors.newFixedThreadPool(1);
		FutureTask timeoutTask = new FutureTask(() -> {
			new CK().calculate(tempdir, ckNotifier);
			return null;
		});
		executor.submit(timeoutTask);

		try {
			timeoutTask.get(timeout, TimeUnit.SECONDS);
		} catch (TimeoutException | InterruptedException | ExecutionException e){
			log.error("CK failed to calculate metrics for " + tempdir + " on the commit " + commitHash
					+ " in the project: " + projectUrl + " with a timeout of " + timeout + " seconds.");
		} finally {
			executor.shutdownNow();
		}
	}

	public static String simplifyFullName(String fullName) {
		if(!fullName.contains("["))
			return fullName;

		String leftPart = fullName.substring(0, fullName.indexOf("["));
		String rightPart = fullName.substring(fullName.indexOf("[") + 1, fullName.length()-1);

		rightPart = cleanGenerics(rightPart);

		String[] parameters = rightPart.split(",");
		String cleanParams = Arrays.stream(parameters).map(p -> {

			if (!p.contains("."))
				return p;
			String[] splitted = p.split("\\.");
			return splitted[splitted.length - 1];
		}).collect(Collectors.joining(","));

		return String.format("%s%s%s%s", leftPart,
				parameters.length > 0 ? "[" : "",
				parameters.length > 0 ? cleanParams : "",
				parameters.length > 0 ? "]" : "");

	}

	// we replace the $ that appears in the name of a class when there is a subclass, e.g., A$B becomes A.B
	// we remove generics, e.g., A<B, C> becomes A
	// Why? Because the way JDT resolves (and stringuifies) class names in TypeDeclarations
	// is different from the way it resolves (and stringuifies) in MethodBinding...
	// We also remove the generic types as RefactoringMiner doesn't return the generics.

	// TODO: maybe the best implementation here is to actually implement a smarter string parser
	// that understands the full syntax...
	private static String cleanGenerics(String clazzName) {
		clazzName = clazzName.replaceAll("\\$", "\\.");

		// while there's a < in the string, we then look for its corresponding >.
		// we then extract this part out of the string.
		// we repeat it until there's no more <
		while(clazzName.contains("<")) {
			int openIndex = clazzName.indexOf("<");
			int qty = 0;
			int closeIndex;
			for (closeIndex = openIndex + 1; closeIndex < clazzName.length(); closeIndex++) {

				char ch = clazzName.charAt(closeIndex);
				if (ch == '<')
					qty++;
				else if (ch == '>') {
					if (qty == 0)
						break;

					qty--;
				}
			}

			String leftPart = clazzName.substring(0, openIndex);
			String rightPart = closeIndex + 1 == clazzName.length() ? "" : clazzName.substring(closeIndex + 1);
			clazzName = leftPart + rightPart;
		}

		return clazzName.trim();
	}

	public static String cleanClassName(String clazzName) {
		return clazzName.replaceAll("\\$", "\\.");
	}

	/*
	Only works with the class type from ck.
	 */
	public static boolean evaluateSubclass(String classType) { return classType.equals("innerclass"); }

	//Extract the class metrics from a CKClassResult
	public static ClassMetric extractClassMetrics(CKClassResult ck){
		return new ClassMetric(
				CKUtils.evaluateSubclass(ck.getType()),
				ck.getCbo(),
				ck.getWmc(),
				ck.getRfc(),
				ck.getLcom(),
				ck.getNumberOfMethods(),
				ck.getNumberOfStaticMethods(),
				ck.getNumberOfPublicMethods(),
				ck.getNumberOfPrivateMethods(),
				ck.getNumberOfProtectedMethods(),
				ck.getNumberOfDefaultMethods(),
				ck.getNumberOfAbstractMethods(),
				ck.getNumberOfFinalMethods(),
				ck.getNumberOfSynchronizedMethods(),
				ck.getNumberOfFields(),
				ck.getNumberOfStaticFields(),
				ck.getNumberOfPublicFields(),
				ck.getNumberOfPrivateFields(),
				ck.getNumberOfProtectedFields(),
				ck.getNumberOfDefaultFields(),
				ck.getNumberOfFinalFields(),
				ck.getNumberOfSynchronizedFields(),
				ck.getNosi(),
				ck.getLoc(),
				ck.getReturnQty(),
				ck.getLoopQty(),
				ck.getComparisonsQty(),
				ck.getTryCatchQty(),
				ck.getParenthesizedExpsQty(),
				ck.getStringLiteralsQty(),
				ck.getNumbersQty(),
				ck.getAssignmentsQty(),
				ck.getMathOperationsQty(),
				ck.getVariablesQty(),
				ck.getMaxNestedBlocks(),
				ck.getAnonymousClassesQty(),
				ck.getInnerClassesQty(),
				ck.getLambdasQty(),
				ck.getUniqueWordsQty());
	}

	//Extract the method metrics from a CKMethodResult
	public static MethodMetric extractMethodMetrics(CKMethodResult ckMethodResult){
		return new MethodMetric(
				CKUtils.simplifyFullName(ckMethodResult.getMethodName()),
				cleanMethodName(ckMethodResult.getMethodName()),
				ckMethodResult.getStartLine(),
				ckMethodResult.getCbo(),
				ckMethodResult.getWmc(),
				ckMethodResult.getRfc(),
				ckMethodResult.getLoc(),
				ckMethodResult.getReturnQty(),
				ckMethodResult.getVariablesQty(),
				ckMethodResult.getParametersQty(),
				ckMethodResult.getLoopQty(),
				ckMethodResult.getComparisonsQty(),
				ckMethodResult.getTryCatchQty(),
				ckMethodResult.getParenthesizedExpsQty(),
				ckMethodResult.getStringLiteralsQty(),
				ckMethodResult.getNumbersQty(),
				ckMethodResult.getAssignmentsQty(),
				ckMethodResult.getMathOperationsQty(),
				ckMethodResult.getMaxNestedBlocks(),
				ckMethodResult.getAnonymousClassesQty(),
				ckMethodResult.getInnerClassesQty(),
				ckMethodResult.getLambdasQty(),
				ckMethodResult.getUniqueWordsQty()
		);
	}
}
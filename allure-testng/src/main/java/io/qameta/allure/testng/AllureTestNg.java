//package io.qameta.allure.testng;
//
//import io.qameta.allure.Allure;
//import io.qameta.allure.Epic;
//import io.qameta.allure.Feature;
//import io.qameta.allure.Flaky;
//import io.qameta.allure.Lifecycle;
//import io.qameta.allure.Muted;
//import io.qameta.allure.Owner;
//import io.qameta.allure.Severity;
//import io.qameta.allure.Story;
//import io.qameta.allure.model.Label;
//import io.qameta.allure.model.Link;
//import io.qameta.allure.model.Parameter;
//import io.qameta.allure.model.Status;
//import io.qameta.allure.model.TestResult;
//import io.qameta.allure.model.TestResultType;
//import io.qameta.allure.util.ResultsUtils;
//import org.testng.IAttributes;
//import org.testng.IClass;
//import org.testng.IInvokedMethod;
//import org.testng.IInvokedMethodListener2;
//import org.testng.ISuite;
//import org.testng.ITestClass;
//import org.testng.ITestContext;
//import org.testng.ITestListener;
//import org.testng.ITestNGMethod;
//import org.testng.ITestResult;
//import org.testng.internal.ConstructorOrMethod;
//import org.testng.xml.XmlSuite;
//import org.testng.xml.XmlTest;
//
//import java.lang.annotation.Annotation;
//import java.lang.reflect.Executable;
//import java.math.BigInteger;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.Optional;
//import java.util.Set;
//import java.util.UUID;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.function.Consumer;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import static io.qameta.allure.util.ResultsUtils.firstNonEmpty;
//import static io.qameta.allure.util.ResultsUtils.getHostName;
//import static io.qameta.allure.util.ResultsUtils.getThreadName;
//import static java.lang.Math.min;
//import static java.nio.charset.StandardCharsets.UTF_8;
//import static java.util.Comparator.comparing;
//import static java.util.stream.IntStream.range;
//
///**
// * Allure TestNG listener.
// */
//@SuppressWarnings({
//        "PMD.ExcessiveImports", "PMD.TooManyMethods", "PMD.GodClass",
//        "ClassFanOutComplexity", "ClassDataAbstractionCoupling", "PMD.ExcessiveClassLength"
//})
//public class AllureTestNg implements ITestListener, IInvokedMethodListener2 {
//
//    private static final String ALLURE_UUID = "ALLURE_UUID";
//    private static final String MD_5 = "md5";
//
//
//    /**
//     * Store current testng result uuid to attach before/after methods into.
//     */
//    private final ThreadLocal<Current> currentTestResult
//            = InheritableThreadLocal.withInitial(Current::new);
//
//    /**
//     * Store current container uuid for fake containers around before/after methods.
//     */
//    private final ThreadLocal<String> currentTestContainer
//            = InheritableThreadLocal.withInitial(() -> UUID.randomUUID().toString());
//
//    /**
//     * Store uuid for current executable item to catch steps and attachments.
//     */
//    private final ThreadLocal<String> currentExecutable
//            = InheritableThreadLocal.withInitial(() -> UUID.randomUUID().toString());
//
//    /**
//     * Store uuid for class test containers.
//     */
//    private final Map<ITestClass, String> classContainerUuidStorage = new ConcurrentHashMap<>();
//
//    private final Lifecycle lifecycle;
//
//    public AllureTestNg(final Lifecycle lifecycle) {
//        this.lifecycle = lifecycle;
//    }
//
//    public AllureTestNg() {
//        this.lifecycle = Allure.getLifecycle();
//    }
//
//    public Lifecycle getLifecycle() {
//        return lifecycle;
//    }
//
//    @Override
//    @SuppressWarnings({"Indentation", "PMD.ExcessiveMethodLength"})
//    public void onTestStart(final ITestResult testResult) {
//        Current current = currentTestResult.get();
//        if (current.isStarted()) {
//            current = refreshContext();
//        }
//        current.test();
//        final ITestNGMethod method = testResult.getMethod();
//        final ITestClass testClass = method.getTestClass();
//        final Set<Label> labels = new HashSet<>();
//        labels.addAll(Arrays.asList(
//                //Packages grouping
//                new Label().setName("package").setValue(testClass.getName()),
//                new Label().setName("testClass").setValue(testClass.getName()),
//                new Label().setName("testMethod").setValue(method.getMethodName()),
//
//                //xUnit grouping
//                new Label().setName("parentSuite").setValue(safeExtractSuiteName(testClass)),
//                new Label().setName("suite").setValue(safeExtractTestTag(testClass)),
//                new Label().setName("subSuite").setValue(safeExtractTestClassName(testClass)),
//
//                //Timeline grouping
//                new Label().setName("host").setValue(getHostName()),
//                new Label().setName("thread").setValue(getThreadName())
//        ));
//        labels.addAll(getLabels(testResult));
//        final Set<Parameter> parameters = getParameters(testResult);
//        final TestResult result = new TestResult()
//                .setUuid(current.getUuid())
//                .setType(TestResultType.TEST)
//                .setHistoryId(getHistoryId(method, parameters))
//                .setName(getMethodName(method))
//                .setFullName(getQualifiedName(method))
//                .setParameters(parameters)
//                .setLinks(getLinks(testResult))
//                .setLabels(labels);
//        getLifecycle().startTest(result);
//
//        final String uuid = current.getUuid();
//        Optional.of(testResult)
//                .map(ITestResult::getMethod)
//                .map(ITestNGMethod::getTestClass)
//                .map(classContainerUuidStorage::get)
//                .ifPresent(testClassContainerUuid -> getLifecycle().updateTestContainer(
//                        testClassContainerUuid,
//                        container -> container.getChildren().add(uuid)
//                ));
//    }
//
//    @Override
//    public void onTestSuccess(final ITestResult testResult) {
//        final Current current = currentTestResult.get();
//        current.after();
//        getLifecycle().updateTestCase(current.getUuid(), setStatus(Status.PASSED));
//        getLifecycle().stopTestCase(current.getUuid());
//        getLifecycle().writeTestCase(current.getUuid());
//    }
//
//    @Override
//    public void onTestFailure(final ITestResult result) {
//        Current current = currentTestResult.get();
//
//        if (current.isAfter()) {
//            current = refreshContext();
//        }
//
//        //if testng has failed without any setup
//        if (!current.isStarted()) {
//            createTestResultForTestWithoutSetup(result);
//        }
//
//        current.after();
//        final Throwable throwable = result.getThrowable();
//        final Status status = getStatus(throwable);
//        final StatusDetails details = getStatusDetails(throwable).orElse(null);
//        getLifecycle().updateTestCase(current.getUuid(), setStatus(status, details));
//        getLifecycle().stopTestCase(current.getUuid());
//        getLifecycle().writeTestCase(current.getUuid());
//    }
//
//    @Override
//    public void onTestSkipped(final ITestResult result) {
//        Current current = currentTestResult.get();
//
//        //testng is being skipped as dependent on failed testng, closing context for previous testng here
//        if (current.isAfter()) {
//            current = refreshContext();
//        }
//
//        //if testng was skipped without any setup
//        if (!current.isStarted()) {
//            createTestResultForTestWithoutSetup(result);
//        }
//        current.after();
//        final StatusDetails details = getStatusDetails(result.getThrowable()).orElse(null);
//        getLifecycle().updateTestCase(current.getUuid(), setStatus(Status.SKIPPED, details));
//        getLifecycle().stopTestCase(current.getUuid());
//        getLifecycle().writeTestCase(current.getUuid());
//    }
//
//    private void createTestResultForTestWithoutSetup(final ITestResult result) {
//        onTestStart(result);
//        currentTestResult.remove();
//    }
//
//    @Override
//    public void onTestFailedButWithinSuccessPercentage(final ITestResult result) {
//        //do nothing
//    }
//
//    @Override
//    public void onStart(final ITestContext context) {
//        //do nothing
//    }
//
//    @Override
//    public void onFinish(final ITestContext context) {
//        //do nothing
//    }
//
//    @Override
//    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult) {
//        //do nothing
//    }
//
//    @Override
//    public void beforeInvocation(final IInvokedMethod method, final ITestResult testResult,
//                                 final ITestContext context) {
//        final ITestNGMethod testMethod = method.getTestMethod();
//        if (isSupportedConfigurationFixture(testMethod)) {
//            ifSuiteFixtureStarted(context.getSuite(), testMethod);
//            ifTestFixtureStarted(context, testMethod);
//            ifClassFixtureStarted(testMethod);
//            ifMethodFixtureStarted(testMethod);
//        }
//    }
//
//    private void ifSuiteFixtureStarted(final ISuite suite, final ITestNGMethod testMethod) {
//        if (testMethod.isBeforeSuiteConfiguration()) {
//            startBefore(getUniqueUuid(suite), testMethod);
//        }
//        if (testMethod.isAfterSuiteConfiguration()) {
//            startAfter(getUniqueUuid(suite), testMethod);
//        }
//    }
//
//    private void ifClassFixtureStarted(final ITestNGMethod testMethod) {
//        if (testMethod.isBeforeClassConfiguration()) {
//            final String parentUuid = classContainerUuidStorage.get(testMethod.getTestClass());
//            startBefore(parentUuid, testMethod);
//        }
//        if (testMethod.isAfterClassConfiguration()) {
//            final String parentUuid = classContainerUuidStorage.get(testMethod.getTestClass());
//            startAfter(parentUuid, testMethod);
//        }
//    }
//
//    private void ifTestFixtureStarted(final ITestContext context, final ITestNGMethod testMethod) {
//        if (testMethod.isBeforeTestConfiguration()) {
//            startBefore(getUniqueUuid(context), testMethod);
//        }
//        if (testMethod.isAfterTestConfiguration()) {
//            startAfter(getUniqueUuid(context), testMethod);
//        }
//    }
//
//    private void startBefore(final String parentUuid, final ITestNGMethod method) {
//        final String uuid = currentExecutable.get();
//        getLifecycle().startPrepareFixture(parentUuid, uuid, getResult(method));
//    }
//
//    private void startAfter(final String parentUuid, final ITestNGMethod method) {
//        final String uuid = currentExecutable.get();
//        getLifecycle().startTearDownFixture(parentUuid, uuid, getResult(method));
//    }
//
//    private void ifMethodFixtureStarted(final ITestNGMethod testMethod) {
//        currentTestContainer.remove();
//        Current current = currentTestResult.get();
//        final FixtureResult fixture = getResult(testMethod);
//        final String uuid = currentExecutable.get();
//        if (testMethod.isBeforeMethodConfiguration()) {
//            if (current.isStarted()) {
//                currentTestResult.remove();
//                current = currentTestResult.get();
//            }
//            getLifecycle().startPrepareFixture(createFakeContainer(testMethod, current), uuid, fixture);
//        }
//
//        if (testMethod.isAfterMethodConfiguration()) {
//            getLifecycle().startTearDownFixture(createFakeContainer(testMethod, current), uuid, fixture);
//        }
//    }
//
//    private String createFakeContainer(final ITestNGMethod method, final Current current) {
//        final String parentUuid = currentTestContainer.get();
//        final TestResultContainer container = new TestResultContainer()
//                .setUuid(parentUuid)
//                .setName(getQualifiedName(method))
//                .setStart(System.currentTimeMillis())
//                .setDescription(method.getDescription())
//                .setChildren(current.getUuid());
//        getLifecycle().startTestContainer(container);
//        return parentUuid;
//    }
//
//    private String getQualifiedName(final ITestNGMethod method) {
//        return method.getRealClass().getName() + "." + method.getMethodName();
//    }
//
//    private TestResult getResult(final ITestNGMethod method) {
//        return new TestResult()
//                .setName(getMethodName(method))
//                .setStart(System.currentTimeMillis())
//                .setDescription(method.getDescription());
//    }
//
//    @Override
//    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult) {
//        //do nothing
//    }
//
//    @Override
//    public void afterInvocation(final IInvokedMethod method, final ITestResult testResult,
//                                final ITestContext context) {
//        final ITestNGMethod testMethod = method.getTestMethod();
//        if (isSupportedConfigurationFixture(testMethod)) {
//            if (testResult.isSuccess()) {
//                getLifecycle().updateTest(result -> result.setStatus(Status.PASSED));
//            } else {
//                getLifecycle().updateTest(result -> result
//                        .setStatus(getStatus(testResult.getThrowable())));
//            }
//            getLifecycle().stopTest();
//
//            if (testMethod.isBeforeMethodConfiguration() || testMethod.isAfterMethodConfiguration()) {
//                final String containerUuid = currentTestContainer.get();
//                validateContainerExists(getQualifiedName(testMethod), containerUuid);
//                currentTestContainer.remove();
//            }
//        }
//    }
//
//    protected String getHistoryId(final ITestNGMethod method, final Set<Parameter> parameters) {
//        final MessageDigest digest = getMessageDigest();
//        final String testClassName = method.getTestClass().getName();
//        final String methodName = method.getMethodName();
//        digest.update(testClassName.getBytes(UTF_8));
//        digest.update(methodName.getBytes(UTF_8));
//        parameters.stream()
//                .sorted(comparing(Parameter::getName).thenComparing(Parameter::getValue))
//                .forEachOrdered(parameter -> {
//                    digest.update(parameter.getName().getBytes(UTF_8));
//                    digest.update(parameter.getValue().getBytes(UTF_8));
//                });
//        final byte[] bytes = digest.digest();
//        return new BigInteger(1, bytes).toString(16);
//    }
//
//    protected Status getStatus(final Throwable throwable) {
//        return ResultsUtils.getStatus(throwable).orElse(Status.BROKEN);
//    }
//
//    @SuppressWarnings("BooleanExpressionComplexity")
//    private boolean isSupportedConfigurationFixture(final ITestNGMethod testMethod) {
//        return testMethod.isBeforeMethodConfiguration() || testMethod.isAfterMethodConfiguration()
//                || testMethod.isBeforeTestConfiguration() || testMethod.isAfterTestConfiguration()
//                || testMethod.isBeforeClassConfiguration() || testMethod.isAfterClassConfiguration()
//                || testMethod.isBeforeSuiteConfiguration() || testMethod.isAfterSuiteConfiguration();
//    }
//
//    private void validateContainerExists(final String fixtureName, final String containerUuid) {
//        if (Objects.isNull(containerUuid)) {
//            throw new IllegalStateException(
//                    "Could not find container for after method fixture " + fixtureName
//            );
//        }
//    }
//
//    private List<Label> getLabels(final ITestResult result) {
//        return Stream.of(
//                getLabels(result, Epic.class, ResultsUtils::createLabel),
//                getLabels(result, Feature.class, ResultsUtils::createLabel),
//                getLabels(result, Story.class, ResultsUtils::createLabel),
//                getLabels(result, Severity.class, ResultsUtils::createLabel),
//                getLabels(result, Owner.class, ResultsUtils::createLabel)
//        ).reduce(Stream::concat).orElseGet(Stream::empty).collect(Collectors.toList());
//    }
//
//    private <T extends Annotation> Stream<Label> getLabels(final ITestResult result, final Class<T> clazz,
//                                                           final Function<T, Label> extractor) {
//        final List<Label> onMethod = getAnnotationsOnMethod(result, clazz).stream()
//                .map(extractor)
//                .collect(Collectors.toList());
//        if (!onMethod.isEmpty()) {
//            return onMethod.stream();
//        }
//        return getAnnotationsOnClass(result, clazz).stream()
//                .map(extractor);
//    }
//
//    private Set<Link> getLinks(final ITestResult result) {
//        return Stream.of(
//                getAnnotationsOnClass(result, io.qameta.allure.Link.class).stream().map(ResultsUtils::createLink),
//                getAnnotationsOnMethod(result, io.qameta.allure.Link.class).stream().map(ResultsUtils::createLink),
//                getAnnotationsOnClass(result, io.qameta.allure.Issue.class).stream().map(ResultsUtils::createLink),
//                getAnnotationsOnMethod(result, io.qameta.allure.Issue.class).stream().map(ResultsUtils::createLink),
//                getAnnotationsOnClass(result, io.qameta.allure.TmsLink.class).stream().map(ResultsUtils::createLink),
//                getAnnotationsOnMethod(result, io.qameta.allure.TmsLink.class).stream().map(ResultsUtils::createLink)
//        ).reduce(Stream::concat).orElseGet(Stream::empty).collect(Collectors.toSet());
//    }
//
//    private boolean isFlaky(final ITestResult result) {
//        return hasAnnotation(result, Flaky.class);
//    }
//
//    private boolean isMuted(final ITestResult result) {
//        return hasAnnotation(result, Muted.class);
//    }
//
//    private boolean hasAnnotation(final ITestResult result, final Class<? extends Annotation> clazz) {
//        return hasAnnotationOnMethod(result, clazz) || hasAnnotationOnClass(result, clazz);
//    }
//
//    private boolean hasAnnotationOnClass(final ITestResult result, final Class<? extends Annotation> clazz) {
//        return !getAnnotationsOnClass(result, clazz).isEmpty();
//    }
//
//    private boolean hasAnnotationOnMethod(final ITestResult result, final Class<? extends Annotation> clazz) {
//        return !getAnnotationsOnMethod(result, clazz).isEmpty();
//    }
//
//    private <T extends Annotation> List<T> getAnnotationsOnMethod(final ITestResult result, final Class<T> clazz) {
//        return Stream.of(result)
//                .map(ITestResult::getMethod)
//                .filter(Objects::nonNull)
//                .map(ITestNGMethod::getConstructorOrMethod)
//                .map(ConstructorOrMethod::getMethod)
//                .flatMap(method -> Stream.of(method.getAnnotationsByType(clazz)))
//                .collect(Collectors.toList());
//    }
//
//    @SuppressWarnings("unchecked")
//    private <T extends Annotation> List<T> getAnnotationsOnClass(final ITestResult result, final Class<T> clazz) {
//        return Stream.of(result)
//                .map(ITestResult::getTestClass)
//                .filter(Objects::nonNull)
//                .map(IClass::getRealClass)
//                .flatMap(aClass -> Stream.of(aClass.getAnnotationsByType(clazz)))
//                .map(clazz::cast)
//                .collect(Collectors.toList());
//    }
//
//    /**
//     * Returns the unique id for given results item.
//     */
//    private String getUniqueUuid(final IAttributes suite) {
//        if (Objects.isNull(suite.getAttribute(ALLURE_UUID))) {
//            suite.setAttribute(ALLURE_UUID, UUID.randomUUID().toString());
//        }
//        return Objects.toString(suite.getAttribute(ALLURE_UUID));
//    }
//
//    private MessageDigest getMessageDigest() {
//        try {
//            return MessageDigest.getInstance(MD_5);
//        } catch (NoSuchAlgorithmException e) {
//            throw new IllegalStateException("Could not find md5 hashing algorithm", e);
//        }
//    }
//
//    private static String safeExtractSuiteName(final ITestClass testClass) {
//        final Optional<XmlTest> xmlTest = Optional.ofNullable(testClass.getXmlTest());
//        return xmlTest.map(XmlTest::getSuite).map(XmlSuite::getName).orElse("Undefined suite");
//    }
//
//    private static String safeExtractTestTag(final ITestClass testClass) {
//        final Optional<XmlTest> xmlTest = Optional.ofNullable(testClass.getXmlTest());
//        return xmlTest.map(XmlTest::getName).orElse("Undefined testng tag");
//    }
//
//    private static String safeExtractTestClassName(final ITestClass testClass) {
//        return firstNonEmpty(testClass.getTestName(), testClass.getName()).orElse("Undefined class name");
//    }
//
//    private Set<Parameter> getParameters(final ITestResult testResult) {
//        final Stream<Parameter> tagsParameters = testResult.getTestContext()
//                .getCurrentXmlTest().getAllParameters().entrySet()
//                .stream()
//                .map(entry -> new Parameter().setName(entry.getKey()).setValue(entry.getValue()));
//        final String[] parameterNames = Optional.of(testResult)
//                .map(ITestResult::getMethod)
//                .map(ITestNGMethod::getConstructorOrMethod)
//                .map(ConstructorOrMethod::getMethod)
//                .map(Executable::getParameters)
//                .map(Stream::of)
//                .orElseGet(Stream::empty)
//                .map(java.lang.reflect.Parameter::getName)
//                .toArray(String[]::new);
//        final String[] parameterValues = Stream.of(testResult.getParameters())
//                .map(this::convertParameterValueToString)
//                .toArray(String[]::new);
//        final Stream<Parameter> methodParameters = range(0, min(parameterNames.length, parameterValues.length))
//                .mapToObj(i -> new Parameter().setName(parameterNames[i]).setValue(parameterValues[i]));
//        return Stream.concat(tagsParameters, methodParameters)
//                .collect(Collectors.toSet());
//    }
//
//    private String convertParameterValueToString(final Object parameter) {
//        if (Objects.nonNull(parameter) && parameter.getClass().isArray()) {
//            return Arrays.toString((Object[]) parameter);
//        }
//        return Objects.toString(parameter);
//    }
//
//    private String getMethodName(final ITestNGMethod method) {
//        return firstNonEmpty(
//                method.getDescription(),
//                method.getMethodName(),
//                getQualifiedName(method)).orElse("Unknown");
//    }
//
//    private Consumer<TestResult> setStatus(final Status status) {
//        return result -> result.setStatus(status);
//    }
//
//    private Current refreshContext() {
//        currentTestResult.remove();
//        return currentTestResult.get();
//    }
//
//    /**
//     * Describes current test result.
//     */
//    private static class Current {
//        private final String uuid;
//        private CurrentStage currentStage;
//
//        Current() {
//            this.uuid = UUID.randomUUID().toString();
//            this.currentStage = CurrentStage.BEFORE;
//        }
//
//        public void test() {
//            this.currentStage = CurrentStage.TEST;
//        }
//
//        public void after() {
//            this.currentStage = CurrentStage.AFTER;
//        }
//
//        public boolean isStarted() {
//            return this.currentStage != CurrentStage.BEFORE;
//        }
//
//        public boolean isAfter() {
//            return this.currentStage == CurrentStage.AFTER;
//        }
//
//        public String getUuid() {
//            return uuid;
//        }
//    }
//
//    /**
//     * The stage of current result context.
//     */
//    private enum CurrentStage {
//        BEFORE,
//        TEST,
//        AFTER
//    }
//}

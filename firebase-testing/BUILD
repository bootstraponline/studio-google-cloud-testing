load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "firebase-testing",
    # do not sort: must match IML order
    srcs = [
        "src",
        "testSrc",
    ],
    tags = ["managed"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.editor-ui-api[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/adt/idea/android[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.lang-api[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools:idea.jetgroovy[module]",
        "//tools:idea.xdebugger-api[module]",
        "//tools/idea/.idea/libraries:google-api-java-client",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-storage-v1-rev1-1.18.0-rc",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-testing-v1-revsnapshot-1.20.0",
        "//tools/studio/google/cloud/tools/core-plugin:google-cloud-tools-core-as[module]",
        "//tools/studio/google/cloud/tools/google-login-plugin:google-login-as[module]",
        "//tools/idea/.idea/libraries:tightvnc",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/studio/google/cloud/testing/firebase-testing/lib:google-api-services-toolresults-v1beta3-rev20151013-1.20.0",
        "//tools/idea/.idea/libraries:swingx",
        "//tools:idea.execution-openapi[module]",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
    ],
)

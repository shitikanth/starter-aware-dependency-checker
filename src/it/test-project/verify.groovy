def logFile = new File(basedir, 'build.log')
assert logFile.exists() : "build.log not found in ${basedir}"
def output = logFile.text

// Assert that "Unused declared dependencies found" is NOT present
// This should fail initially, confirming the problem.
if (output.contains("Unused declared dependencies found")) {
    throw new IllegalStateException("FAILURE: 'Unused declared dependencies found' was present in the output. Test should fail until analyzer is implemented.")
}

// Assert that "Used undeclared dependencies found" is NOT present
// This should fail initially, confirming the problem.
if (output.contains("Used undeclared dependencies found")) {
    throw new IllegalStateException("FAILURE: 'Used undeclared dependencies found' was present in the output. Test should fail until analyzer is implemented.")
}

println "SUCCESS: No false positives detected. This message should only appear after the analyzer is implemented and working."

return true
package brj

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class MainTest {
    
    @Test
    fun `runs namespace main function with no args`() {
        val output = captureOutput {
            main.main(arrayOf("-m", "main_test:simple"))
        }
        
        assertTrue(output.contains("Hello from main!"))
        assertTrue(output.contains("Args: []"))
    }
    
    @Test
    fun `runs namespace main function with args`() {
        val output = captureOutput {
            main.main(arrayOf("-m", "main_test:simple", "arg1", "arg2"))
        }
        
        assertTrue(output.contains("Hello from main!"))
        assertTrue(output.contains("Args: [\"arg1\", \"arg2\"]") || 
                   output.contains("Args: [arg1, arg2]"))
    }
    
    @Test
    fun `fails when no arguments provided`() {
        val exitCode = captureExit {
            main.main(arrayOf())
        }
        
        assertEquals(1, exitCode)
    }
    
    @Test
    fun `fails when -m flag is missing`() {
        val exitCode = captureExit {
            main.main(arrayOf("main_test:simple"))
        }
        
        assertEquals(1, exitCode)
    }
    
    @Test
    fun `fails when namespace not found`() {
        val exitCode = captureExit {
            main.main(arrayOf("-m", "nonexistent:namespace"))
        }
        
        assertEquals(1, exitCode)
    }
    
    @Test
    fun `fails when main function not defined`() {
        val exitCode = captureExit {
            main.main(arrayOf("-m", "require_test:base"))
        }
        
        assertEquals(1, exitCode)
    }
    
    private fun captureOutput(block: () -> Unit): String {
        val outStream = ByteArrayOutputStream()
        val errStream = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        
        try {
            System.setOut(PrintStream(outStream))
            System.setErr(PrintStream(errStream))
            block()
            return outStream.toString() + errStream.toString()
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }
    
    private fun captureExit(block: () -> Unit): Int {
        val originalSecurityManager = System.getSecurityManager()
        val exitException = ExitException(-1)
        
        System.setSecurityManager(object : SecurityManager() {
            override fun checkExit(status: Int) {
                throw ExitException(status)
            }
            
            override fun checkPermission(perm: java.security.Permission) {
                // Allow all other permissions
            }
        })
        
        try {
            block()
            return 0
        } catch (e: ExitException) {
            return e.status
        } finally {
            System.setSecurityManager(originalSecurityManager)
        }
    }
    
    private class ExitException(val status: Int) : SecurityException()
}

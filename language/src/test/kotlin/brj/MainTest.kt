package brj

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MainTest {
    
    @Test
    fun `runs namespace main function with no args`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run main_test:simple")
        
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Hello from main!"))
        assertTrue(result.output.contains("Args: []"))
    }
    
    @Test
    fun `runs namespace main function with args`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run main_test:simple arg1 arg2")
        
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Hello from main!"))
        assertTrue(result.output.contains("Args: [\"arg1\", \"arg2\"]") || 
                   result.output.contains("Args: [arg1, arg2]"))
    }
    
    @Test
    fun `shows help when no subcommand provided`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("")
        
        assertTrue(result.output.contains("Usage:") || result.output.contains("brj.main"))
    }
    
    @Test
    fun `shows run help when run called without namespace`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run")
        
        assertNotEquals(0, result.statusCode)
    }
    
    @Test
    fun `fails when namespace not found`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run nonexistent:namespace")
        
        assertNotEquals(0, result.statusCode)
        assertTrue(result.output.contains("not found") || result.output.contains("Error"))
    }
    
    @Test
    fun `fails when main function not defined`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run require_test:base")
        
        assertNotEquals(0, result.statusCode)
        assertTrue(result.output.contains("main") || result.output.contains("Error"))
    }
    
    @Test
    fun `runs nested namespace main function`() {
        val command = BridjeMain().subcommands(RunCommand())
        val result = command.test("run main_test:nested:app")
        
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("Nested namespace app"))
    }
}

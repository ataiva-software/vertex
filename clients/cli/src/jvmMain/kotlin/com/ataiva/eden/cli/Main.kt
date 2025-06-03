package com.ataiva.eden.cli

/**
 * Eden DevOps Suite CLI - Main Entry Point
 * Provides command-line interface for system management
 */
fun main(args: Array<String>) {
    val cli = EdenCLI()
    cli.run(args)
}
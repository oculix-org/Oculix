/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.idesupport;

/**
 * Enum that stores the info about the commandline args
 */
public enum CommandArgsEnum {

	/**
	 * Shows the help
	 */
	HELP("help", "h", null, "print this help message"),
	/**
	 * Prints all (error, ...) messages to stdout
	 */
	CONSOLE("console", "c", null, "print all output to stdout / CLI (not to IDE message area)"),
	/**
	 * special debugging especially during startup
	 */
	VERBOSE("verbose", "v", null, "Debug level 3 and elapsed time during startup"),
	/**
	 * special debugging during startup
	 */
	DEBUG("debug", "d", "debug level", "positive integer (1)", true),
	/**
	 * outputfile for Sikuli logging messages
	 */
	QUIET("quiet", "q", null, "show nothing at startup (switches of -v -d)"),
	/**
	 * set debug level
	 */
	LOGFILE("logfile", "f", "Sikuli logfile", "a valid filename (WorkingDir/SikuliLog.txt)", true),
	/**
	 * outputfile for user logging messages
	 */
	USERLOGFILE("userlog", "u", "User logfile", "a valid filename (WorkingDir/UserLog.txt)", true),
	/**
	 * Runs the script
	 */
	RUN("run", "r", "null", "run script (see details below)", true),
	/**
	 * Preloads script in IDE
	 */
	LOAD("load", "l", "null", "preload script in IDE (see details below)", true),
	/**
	 * Auto-runs the preloaded script (requires a single -l file)
	 */
	EXECUTE("execute", "e", null, "auto-run the preloaded script (requires -l file)"),
	/**
	 * allow multiple IDE
	 */
	APPDATA("appdata", "a", "appdata path", "path for SikuliX appdata", true);

	/**
	 * Longname of the parameter
	 */
	private String longname;
	public String longname() {
		return longname;
	}

	/**
	 * Shortname of the parameter
	 */
	private String shortname;
	public String shortname() {
		return shortname;
	}

	/**
	 * The param name
	 */
	private String argname;
	public String argname() {
		return argname;
	}

	/**
	 * The description
	 */
	private String description;
	public String description() {
		return description;
	}

	/**
	 * has args
	 */
	private boolean hasArgs;
	public boolean hasArgs() {
		return hasArgs;
	}

	/**
	 * Private constructor for class CommandArgsEnum.
	 *
	 * @param longname The long name for the param
	 * @param shortname The short name for the param
	 * @param argname The argname
	 * @param description The description for the Command Args
	 */
	private CommandArgsEnum(String longname, String shortname, String argname, String description, boolean hasArgs) {
		this.longname = longname;
		this.shortname = shortname;
		this.argname = argname;
		this.description = description;
		this.hasArgs = hasArgs;
	}

	// variant having no args
	private CommandArgsEnum(String longname, String shortname, String argname, String description) {
		this.longname = longname;
		this.shortname = shortname;
		this.argname = argname;
		this.description = description;
		this.hasArgs = false;
	}
}

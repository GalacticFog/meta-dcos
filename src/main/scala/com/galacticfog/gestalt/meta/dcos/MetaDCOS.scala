import java.util.StringTokenizer
import java.util.Vector

import com.galacticfog.gestalt.cli.DefaultConsole
import com.galacticfog.gestalt.cli.DefaultShell
import com.galacticfog.gestalt.meta.dcos.DCOSShell
import joptsimple.OptionParser
import joptsimple.OptionSet

object MetaDCOS extends App {

	val parser = new OptionParser()
		parser.accepts("user").withOptionalArg
		parser.accepts("password").withOptionalArg
		parser.accepts("meta-hostname").withOptionalArg
		parser.accepts("meta-port").withOptionalArg
		parser.accepts("env").withOptionalArg
		parser.accepts("provider").withOptionalArg


	val argString = args.mkString( " " )
	val opts : OptionSet = parser.parse(translate(argString) : _*)


	try {
		args foreach { a => println("arg : " + a) }
		login(args)

		val cli = initConsole( opts )
		cli.start(args.toList)

	}
	catch {
		case e: Throwable => {
			e.printStackTrace
			println("[fatal]: " + e.getMessage)
		}
	}

	def login(args: Array[String]) = {
		true
	}

	def initConsole(opts : OptionSet) = {
		new DCOSShell(new DefaultConsole, "meta-dcos ->", options = opts )
	}


	def showCommand(opts: OptionSet) = println {
		"-" * 25 + 
			"\nuser: %-10s\npassword: %-10s\nmeta: %-10s".format(
					opts.valueOf("user"), 
					opts.valueOf("password"), 
					opts.valueOf("meta"))
	}

	object ParseState {
		val NORMAL = 0
			val IN_QUOTE = 1
			val IN_DOUBLE_QUOTE = 2 
	}

	def translate(toProcess: String) = {

		import ParseState._    

			if (toProcess == null || toProcess.isEmpty) {
				Array[String]()
			}

		// parse with a simple finite state machine
		var state = NORMAL;

		val tok = new StringTokenizer(toProcess, "\"\' ", true)
			val v = new Vector[String]
			var current = new StringBuffer()

			var lastTokenHasBeenQuoted = false;

		while (tok.hasMoreTokens()) {

			val nextTok = tok.nextToken();

			state match {
				case IN_QUOTE => {
					if ("\'".equals( nextTok )) {
						lastTokenHasBeenQuoted = true
							state = NORMAL
					} else {
						current.append( nextTok )
					}
				}
				case IN_DOUBLE_QUOTE => {
					if ("\"".equals( nextTok )) {
						lastTokenHasBeenQuoted = true
							state = NORMAL
					} else {
						current.append( nextTok )            
					}
				}
				case _ => {
					if ("\'".equals(nextTok))      state = IN_QUOTE;
					else if ("\"".equals(nextTok)) state = IN_DOUBLE_QUOTE;
					else if (" ".equals(nextTok)) {
						if (lastTokenHasBeenQuoted || current.length() != 0) {
							v.addElement(current.toString());
							current = new StringBuffer();
						}

					} 
					else {
						current.append(nextTok);
					}
					lastTokenHasBeenQuoted = false;          
				}
			}
		} // while

		if (lastTokenHasBeenQuoted || current.length() != 0) {
			v.addElement(current.toString());
		}

		if (state == IN_QUOTE || state == IN_DOUBLE_QUOTE) {
			throw new IllegalArgumentException("unbalanced quotes in " + toProcess);
		}

		val args = new Array[Object]( v.size );
		v.copyInto(args);
		args map { a => a.toString };
	}  
}

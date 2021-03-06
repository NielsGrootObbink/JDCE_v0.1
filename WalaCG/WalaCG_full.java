// Code mostly from com.ibm.wala.cast.js/source/com/ibm/wala/cast/js/util/CallGraph2JSON.java
// Adapted for easier output parsing: removed line number, change file path to relative path.


package com.nielsgrootobbink.WalaCG;

import java.io.IOException;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.types.JavaScriptMethods;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.functions.Function;

public class WalaCG {

	public static void main(String[] args) throws IllegalArgumentException,
			IOException, CancelException, WalaException {

		if (args.length < 2) {
			System.out.println("Usage: WalaCG <folder> <html_file>");
			System.exit(1);
		}

		String folder = args[0];
		String html_file = args[1];

		Path p = Paths.get(folder, html_file);

		File f = new File(p.toString());
		URL url_of_html_file = f.toURI().toURL();

		com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil
				.setTranslatorFactory(new CAstRhinoTranslatorFactory());

		JSCFABuilder builder = com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil
				.makeHTMLCGBuilder(url_of_html_file);

		CallGraph CG = builder.makeCallGraph(builder.getOptions());

		System.out.println(serialize(CG, html_file, folder));
	}

	public static String serialize(CallGraph cg, String html_file_name,
			String base_folder) {
		Map<String, Set<String>> edges = extractEdges(cg, base_folder);
		return toJSON(edges, html_file_name);
	}

	public static Map<String, Set<String>> extractEdges(CallGraph cg,
			String base_folder) {
		Map<String, Set<String>> edges = HashMapFactory.make();
		for (CGNode nd : cg) {
			if (!isRealFunction(nd.getMethod()))
				continue;

			AstMethod method = (AstMethod) nd.getMethod();

			for (Iterator<CallSiteReference> iter = nd.iterateCallSites(); iter
					.hasNext();) {

				CallSiteReference callsite = iter.next();

				Set<IMethod> targets = com.ibm.wala.util.collections.Util
						.mapToSet(cg.getPossibleTargets(nd, callsite),
								new Function<CGNode, IMethod>() {
									@Override
									public IMethod apply(CGNode nd) {
										return nd.getMethod();
									}
								});

				serializeCallSite(method, callsite, targets, edges, base_folder);
			}
		}
		return edges;
	}

	public static void serializeCallSite(AstMethod method,
			CallSiteReference callsite, Set<IMethod> targets,
			Map<String, Set<String>> edges, String base_folder) {
		Set<String> targetNames = MapUtil.findOrCreateSet(
				edges,
				ppPos(method,
						method.getSourcePosition(callsite.getProgramCounter()),
						base_folder));
		for (IMethod target : targets) {
			target = getCallTargetMethod(target);
			if (!isRealFunction(target))
				continue;

			targetNames.add(ppPos((AstMethod) target,
					((AstMethod) target).getSourcePosition(), base_folder));
		}
	}

	private static IMethod getCallTargetMethod(IMethod method) {
		if (method.getName().equals(JavaScriptMethods.ctorAtom)) {
			method = method.getDeclaringClass().getMethod(
					AstMethodReference.fnSelector);
			if (method != null)
				return method;
		}
		return method;
	}

	public static boolean isRealFunction(IMethod method) {
		if (method instanceof AstMethod) {
			String methodName = method.getDeclaringClass().getName().toString();

			// exclude synthetic DOM modelling functions
			if (methodName.contains("/make_node"))
				return false;

			for (String bootstrapFile : JavaScriptLoader.bootstrapFileNames)
				if (methodName.startsWith("L" + bootstrapFile + "/"))
					return false;

			return method.getName().equals(AstMethodReference.fnAtom);
		}
		return false;
	}

	private static String ppPos(AstMethod method, Position pos,
			String base_folder) {
		URL url = pos.getURL();

		String gf = url.getFile();
		int index = gf.indexOf(base_folder);
		String file = gf;

		if (index > -1) {
			file = gf.substring(index);
		}

		int start_offset = pos.getFirstOffset(), end_offset = pos
				.getLastOffset();

		return file + "@" + start_offset + "-" + end_offset;
	}

	public static String toJSON(Map<String, Set<String>> map,
			final String html_file_name) {
		StringBuffer res = new StringBuffer();

		// System.out.println("");System.out.println("");
		res.append("{\n");

		res.append(joinWith(com.ibm.wala.util.collections.Util.mapToSet(
				map.entrySet(),
				new Function<Map.Entry<String, Set<String>>, String>() {
					@Override
					public String apply(Map.Entry<String, Set<String>> e) {
						StringBuffer res = new StringBuffer();
						if (e.getValue().size() > 0) {
							String k = e.getKey();
							res.append("    \"" + k + "\": [\n");
							res.append(joinWith(
									com.ibm.wala.util.collections.Util.mapToSet(
											e.getValue(),
											new Function<String, String>() {
												@Override
												public String apply(String str) {
													return "        \"" + str
															+ "\"";
												}
											}), ",\n"));
							res.append("\n    ]");
						}
						return res.length() == 0 ? null : res.toString();
					}
				}), ",\n"));

		res.append("\n}");
		return res.toString();
	}

	private static String joinWith(Iterable<String> lst, String sep) {
		StringBuffer res = new StringBuffer();
		ArrayList<String> strings = new ArrayList<String>();
		for (String s : lst)
			if (s != null)
				strings.add(s);

		boolean fst = true;
		for (String s : strings) {
			if (fst)
				fst = false;
			else
				res.append(sep);
			res.append(s);
		}
		return res.toString();
	}
}

package models;

import java.util.LinkedList;
import java.util.List;

public class JSTreeNode {
	public String id;
	public String text;
	public String icon;
	public JSTreeState state = new JSTreeState(false, false, false);
	public List<JSTreeNode> children = new LinkedList<JSTreeNode>();
	
	public JSTreeNode(String id, String text) {
		this.id = id;
		this.text = text;
	}
	
	public JSTreeNode(String text) {
		this("", text);
	}
	
	public JSTreeNode() {
		this("", null);
	}
}

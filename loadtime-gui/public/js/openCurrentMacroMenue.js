/**
 * 
 */
var getCurrentMacroRef = function(){
	var result = "";
	var loc = location.href.split("#");
	var locationArray = loc[0].split("/");
	var start = locationArray.indexOf(location.host);
	if(locationArray.length >= start+4){
		result = "/"+locationArray[start+1]+"/"+locationArray[start+2]+"/"+locationArray[start+3];
	}
	return result;
}

var markCurrentMacroInMenu = function(){
	var macro = getCurrentMacroRef();
	var a = $("div a[href='"+macro+"']");
	var div = a.parent();
	div.collapse();
	a.addClass("currentMacro");
}

$(markCurrentMacroInMenu);
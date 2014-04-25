var myPanelMenue;

var initMyPanelMenue = function(){
	myPanelMenue = $("#MyPanelMenue");
	if(myPanelMenue.length > 0){
		jQuery.fn.extend({
			scrollToMe: function () {
				var x = jQuery(this).offset().top - 100;
				jQuery('html,body').animate({scrollTop: x}, 500);
			}
		});
		var panels = $("h3.panel-title, .my_menue_entry");
		for(i=0; i<panels.length; i++){
			addToPanelMenue(panels[i]);
		}
	}
}

var addToPanelMenue = function(panel){
	var entry = getNewEntry(panel);
	myPanelMenue.append(entry);
}

var getNewEntry = function(panel){
	var li = $(document.createElement("li"));
	li.addClass("myPanelMenueEntry");
	
	if($(panel).hasClass("my_menue_entry")){
		li.addClass("myPanelMenueSubEntry");
	}
	
	var text = $(panel).text()
	if($(panel).attr("additional_info") != undefined){
		text = text.concat(" (" + $(panel).attr("additional_info") + ")");		
	}
	
	li.text(text);

	li.click(function(){
		$(panel).scrollToMe();
	});
	
	return li;
}
$(initMyPanelMenue);
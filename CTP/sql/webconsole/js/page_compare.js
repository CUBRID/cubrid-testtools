
var start, stop;
var paste_cont; //win_copy.jsp will use

var msie=/msie/.test(navigator.userAgent.toLowerCase());

$(document).ready(function(){

     $('#main_table tr td:nth-child(2)').each(function(index, e) {
         $(e).bind("mousedown", function(){
		start=index;
		paste_cont='';
	 });
     $(e).bind("mouseup", function(){
		stop=index;
		if(start>stop) {
		    var t=start;
		    start=stop;
		    stop=t;
		}
		if(stop==0) return;
		if(start==0) start =1;
		paste_cont = $('#main_table tr').slice(start, stop+1).find("td:nth-child(2)").text();
    
		//if(msie) window.clipboardData.setData("Text",paste_cont);	

		window.open("win_copy.jsp", "_win_copy");
	});
  });
  
  $("#btn_source").bind("click", function(){
	  paste_cont = $('#main_table tr:gt(0) td:nth-child(2)').text();
	  window.open("win_copy.jsp", "_win_copy");
  });
  
});


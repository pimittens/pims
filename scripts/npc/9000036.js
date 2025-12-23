/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/* NPC: Agent E (9000036)
	Victoria Road : Henesys
	
	Refining NPC:
	* Accessories refiner
        * 
        * @author Ronan Lana
*/

var status = -1;
var selectedType = -1;
var selectedItem = -1;
var item;
var items;
var mats;
var matQty;
var cost;
var qty = 1;
var equip;
var maxEqp = 0;

function start() {
    if (!Packages.config.YamlConfig.config.server.USE_ENABLE_CUSTOM_NPC_SCRIPT) {
        cm.sendOk("Hi, I'm #b#p" + cm.getNpc() + "##k.");
        cm.dispose();
        return;
    }
    
    cm.getPlayer().setCS(true);
    var selStr = "Hello, I am the #bAccessory NPC Crafter#k! My works are widely recognized to be too fine, up to the point at which all my items mimic not only the appearance but too the attributes of them! Everything I charge is some 'ingredients' to make them and, of course, a fee for my services. On what kind of equipment are you interessed?#b";
    var options = ["Summon hard to find monsters","Buy eye of fire","Buy piece of cracked dimension","Medals","Scrolls","Chairs","Rings","Pendants","Face accessories","Eye accessories","Belts"/*,"#t4032496#"*/];
	//var options = ["Pendants","Face accessories","Eye accessories","Belts","Medals","Rings"/*,"#t4032496#"*/];
    for (var i = 0; i < options.length; i++)
        selStr += "\r\n#L" + i + "# " + options[i] + "#l";
    cm.sendSimple(selStr);
}

function action(mode, type, selection) {
    status++;
    if (mode != 1) {
        cm.dispose();
        return;
    }
    if (status == 0) {
		if (selection == 1 || selection == 2) {
			if (selection == 1) { //eof
				item = 4001017;
				cost = 1000000;
			}else if (selection == 2) { //crack
				item = 4031179;
				cost = 100000;
			}
			status = 8;
			cm.sendGetNumber("#i" + item + "# cost " + cost + " each. How many would you like to buy?", 1, 1, 100);
		} else {
			if (selection == 0) { //summons monsters
				var selStr = "Choose a monster to summon. You must be at least level 120 to use this service#b";
				items = [3000001, 3230300, 3300005, 3300006, 3300007, 3300008, 4130103, 5120100, 5090000, 5090001, 6090000, 7130400, 7130401, 7130402, 8090000, 9303014, 6090001, 7090000, 6090003, 6090004];
				
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##o" + items[i] + "##b";
				status = 7;
			}else if (selection == 3) { //medals
				var selStr = "the stats of the medal you recieve will be determined by the number of cards in your monster book#b";
				items = [];
				maxEqp = 0;
				
				for (var x = 1142000; x < 1142102; maxEqp++, x++)
					items[maxEqp] = x;
				
				for (var x = 1142107; x < 1142121; maxEqp++, x++)
					items[maxEqp] = x;
			
				for (var x = 1142122; x < 1142143; maxEqp++, x++)
					items[maxEqp] = x;
				
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##t" + items[i] + "##b";
				status = 6;
			}else if (selection == 4) { //scrolls
				var selStr = "You can exchange maple leaves for the following scrolls#b";
				items = [2040727, 2041058, 2049200, 2049202, 2049204, 2049206, 2040914, 2040919];
				
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##z" + items[i] + "##b";
				status = 9;
			}else if (selection == 5) { //chairs
				var selStr = "You can exchance 100 maple leaves for a random chair#b";
				items = [3010000, 3010001, 3010002, 3010003, 3010004, 3010005, 3010006, 3010007, 3010008, 3010009, 3010010, 3010011, 3010012, 3010013, 3010014, 3010015, 3010016, 3010017, 3010018, 3010019, 3010025, 3010040, 3010041, 3010043, 3010045, 3010046, 3010047, 3010057, 3010058, 3010060, 3010061, 3010062, 3010063, 3010064, 3010065, 3010066, 3010067, 3010069, 3010071, 3010072, 3010073, 3010080, 3010081, 3010082, 3010083, 3010084, 3010085, 3010092, 3010098, 3010099, 3010101, 3010106, 3010111, 3010116, 3012005, 3012010, 3012011];
				maxEqp = 57;

				selStr += "\r\n#L" + i + "##bTry it!#b";
			}else if (selection == 6) { //ring refine
				var selStr = "Rings, huh? These are my specialty, go check it out yourself!#b";
				items = [1112407, 1112408, 1112401, 1112402, 1112413, 1112414, 1112405];
				
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##z" + items[i] + "##b";
				
			/*}else if (selection == 5) { //make necklace
				var selStr = "Need to make #t4032496#?#b";
				items = [4032496];
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##t" + items[i] + "##l";
			}*/
			}else if (selection == 7) { //pendants
				var selStr = "Well, I've got these pendants on my repertoire:#b";
				items = [1122018,1122001,1122003,1122004,1122006,1122002,1122005,1122058];
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##z" + items[i] + "##b";
			}else if (selection == 8) { //face accessory
				var selStr = "Hmm, face accessories? There you go: #b";
				items = [1012181,1012182,1012183,1012184,1012185,1012186, 1012108, 1012109, 1012110, 1012111, 1012070, 1012071, 1012072, 1012073];
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##z" + items[i] + "##b";
			}else if (selection == 9) { //eye accessory
				var selStr = "Got hard sight? Okay, so which glasses do you want me to make?#b";
				items = [1022088, 1022103, 1022089, 1022082];
				for (var i = 0; i < items.length; i++)
					selStr += "\r\n#L" + i + "##i" + items[i] + "##z" + items[i] + "##b";
			}else if (selection == 10) { //belt
				var selStr = "Hmm... For these, things get a little tricky. Since these items are too short and too similar one another, I don't really know what item will emerge when I finish the synthesis. Still wanna try for something?";
				items = [];
				maxEqp = 0;
				
				for (var x = 1132005; x < 1132017; maxEqp++, x++)
					items[maxEqp] = x;	
				selStr += "\r\n#L" + i + "##bTry it!#b";
				
			/*}else if (selection == 4) { //medal
				var selStr = "Hmm... For these, things get a little tricky. Since these items are too short and too similar one another, I don't really know what item will emerge when I finish the synthesis. Still wanna try for something?";
				items = [];
				maxEqp = 0;
				
				for (var x = 1142000; x < 1142102; maxEqp++, x++)
					items[maxEqp] = x;
				
				for (var x = 1142107; x < 1142121; maxEqp++, x++)
					items[maxEqp] = x;
			
				for (var x = 1142122; x < 1142143; maxEqp++, x++)
					items[maxEqp] = x;		
				selStr += "\r\n#L" + i + "##bTry it!#b";
				*/
			}
			selectedType = selection;
			cm.sendSimple(selStr);
		}
    }else if (status == 1) {
        if (selectedType != 10 && selectedType != 5) selectedItem = selection;
        
        if (selectedType == 7) { //pendant refine
            var matSet = [[4003004, 4030012, 4001356, 4000026], [4001343, 4011002, 4003004, 4003005], [4001343, 4011006, 4003004, 4003005], [4000091, 4011005, 4003004, 4003005], [4000091, 4011001, 4003004, 4003005], [4000469, 4011000, 4003004, 4003005], [4000469, 4011004, 4003004, 4003005], [1122007, 4003002, 4000413]];
            var matQtySet = [[20, 20, 5, 1], [10, 2, 20, 4], [10, 1, 20, 4], [15, 3, 30, 6], [15, 3, 30, 6], [20, 5, 20, 8], [20, 4, 40, 8], [1, 1, 1]];
            var costSet = [5000000, 200000, 200000, 300000, 300000, 400000, 400000, 2500000];
        }else if (selectedType == 8) { //face accessory refine
            var matSet = [[4006000, 4003004],[4006000, 4003004,4000026],[4006000, 4003004,4000026,4000082,4003002],[4006000, 4003005],[4006000, 4003005,4000026],[4006000, 4003005,4000026,4000082,4003002],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008],[4001006, 4011008]];
            var matQtySet = [[5,5],[5,5,5],[5,5,5,5,1],[5,5],[5,5,5],[5,5,5,5,1],[1,1],[1,1],[1,1],[1,1],[1,1],[1,1],[1,1],[1,1]];
            var costSet = [100000,200000,300000,125000,250000,375000,500000,500000,500000,500000, 25000000, 25000000, 25000000, 25000000, 25000000, 25000000, 25000000, 25000000];
        }else if (selectedType == 9) { //eye accessory refine
            var matSet = [[4001005, 4011008], [4001005, 4011008], [4001005, 4011008, 4000082], [4001006, 4003002, 4032133]];
            var matQtySet = [[3, 2], [4, 3], [5, 3, 10], [2, 2, 10]];
            var costSet = [25000000, 30000000, 40000000, 40000000];
        }else if (selectedType == 10) { //belt refine
            var matSet = [[4001006, 4003005, 4003004], [7777, 7777]];
            var matQtySet = [[2, 5, 10], [7777, 7777]];
            var costSet = [150000, 7777];
        /*}else if (selectedType == 4) { //medals refine
            var matSet = [[4001006, 4003005, 4003004], [7777, 7777]];
            var matQtySet = [[2, 5, 10], [7777, 7777]];
            var costSet = [15000, 7777];*/
        }else if (selectedType == 6) { //ring refine
            var matSet = [[4003001, 4001344, 4006000], [4003001, 4001344, 4006000], [4021004, 4011008], [4011007, 4021009], [4011008, 4001006], [1112413, 2022039], [1112414, 4000176]];
            var matQtySet = [[2, 2, 2], [2, 2, 2], [1, 1], [1, 1], [1, 1], [1, 1], [1, 1]];
            var costSet = [1000000, 1000000, 1000000, 2000000, 1500000, 1500000, 2000000];
        /*}else if (selectedType == 5) { //necklace refine
            var matSet = [[4011007, 4011008, 4021009]];
            var matQtySet = [[1, 1, 1]];
            var costSet = [10000];
        }*/
		}else if (selectedType == 5) { //chairs
			var matSet = [[4001126]];
            var matQtySet = [[100]];
            var costSet = [1000000];
		}
        
        if (selectedType == 10) {// || selectedType == 4) {
            selectedItem = Math.floor(Math.random() * maxEqp);
            item = items[selectedItem];
            mats = matSet[0];
            matQty = matQtySet[0];
            cost = costSet[0];
        }else if (selectedType == 5) {
			selectedItem = Math.floor(Math.random() * maxEqp);
            item = items[selectedItem];
			var limit = 0;
			while (limit < 100 && cm.haveItem(item, 1)) {
				selectedItem = Math.floor(Math.random() * maxEqp);
				item = items[selectedItem];
				limit += 1;
			}
            mats = matSet[0];
            matQty = matQtySet[0];
            cost = costSet[0];
		}else {
            item = items[selectedItem];
            mats = matSet[selectedItem];
            matQty = matQtySet[selectedItem];
            cost = costSet[selectedItem];
        }
        
        var prompt = "You want me to make ";
        if(selectedType == 10) {
            prompt += "a #bbelt#k?";
        }else if (selectedType == 5) {
			prompt += "a #bchair#k?";
        }else {
			if (qty == 1)
                prompt += "a #b#t" + item + "##k?";
            else
                prompt += "#b" + qty + " #t" + item + "##k?";
		}
        
        prompt += " Right! I will need some items to make that item. Make sure you have a #bfree slot#k in your inventory!#b";
        if (mats instanceof Array)
            for(var i = 0; i < mats.length; i++)
                prompt += "\r\n#i" + mats[i] + "# " + (matQty[i] * qty) + " #t" + mats[i] + "#";
        else
            prompt += "\r\n#i" + mats + "# " + (matQty * qty) + " #t" + mats + "#";
        if (cost > 0)
            prompt += "\r\n#i4031138# " + (cost * qty) + " meso";
        cm.sendYesNo(prompt);
    }else if (status == 2) {
        if (cm.getMeso() < (cost * qty)) {
            cm.sendOk("This is the fee I charge to make my items! No credit.");
        } else {
            var complete = true;
            if (mats instanceof Array) {
                for(var i = 0; complete && i < mats.length; i++)
                    if (!cm.haveItem(mats[i], matQty[i] * qty))
                        complete = false;
            } else if (!cm.haveItem(mats, matQty * qty))
                complete = false;
            
            if (!complete)
                cm.sendOk("Are you sure you have all the items required? Double check!");
            else {
                if (cm.canHold(item, qty)) {
                    if (mats instanceof Array) {
                        for (var i = 0; i < mats.length; i++)
                            cm.gainItem(mats[i], -(matQty[i] * qty));
                    } else
                        cm.gainItem(mats, -(matQty * qty));
                    cm.gainMeso(-(cost * qty));

                    cm.gainItem(item, qty);
                    cm.sendOk("The item is done! Take it and try this piece of art yourself.");
                } else {
                    cm.sendOk("You have no free slot in your inventory.");
                }
            }
        }	
        
        cm.dispose();
    }else if (status == 7) {
		if (cm.canHold(items[selection], 1)) {
			cm.gainMonsterBookMedal(items[selection]);
			cm.sendOk("The item is done! Take it and try this piece of art yourself.");
		} else {
            cm.sendOk("You have no free slot in your inventory.");
        }
		cm.dispose();
	}else if (status == 8) {
		selectedItem = selection;
		item = items[selectedItem];
		if (cm.getPlayer().getLevel() >= 120) {
			for (var i = 0; i < 10; i++) {
				cm.getPlayer().getMap().spawnMonsterOnGroundBelow(item, cm.getPlayer().getPosition().x, cm.getPlayer().getPosition().y);
			}
		} else {
			cm.sendOk("You must be level 120 or higher to use this service.");
		}
		cm.dispose();
	}else if (status == 9) {
		if (cm.getMeso() < (cost * selection)) {
            cm.sendOk("This is the fee I charge to make my items! No credit.");
        } else {
			if (cm.canHold(item, selection)) {
				cm.gainMeso(-(cost * selection));
				cm.gainItem(item, selection);
				cm.sendOk("The item is done! Take it and try this piece of art yourself.");
			} else {
				cm.sendOk("You have no free slot in your inventory.");
			}
        }
		cm.dispose();
	}else if (status == 10) {
		selectedItem = selection;
		var matSet = [[4001126], [4001126], [4001126], [4001126], [4001126], [4001126], [4001126], [4001126]];
		var matQtySet = [[5], [5], [20], [20], [20], [20], [30], [30]];
		var costSet = [10000, 10000, 50000, 50000, 50000, 50000, 5000000, 5000000];
		item = items[selectedItem];
		mats = matSet[selectedItem];
		matQty = matQtySet[selectedItem];
		cost = costSet[selectedItem];
		var prompt = "You want me to make ";
		cm.sendGetNumber("#i" + item + "# cost " + cost + " each. How many would you like to buy?", 1, 1, 100);
	}else if (status == 11) {
		qty = selection;
		var prompt = "You want me to make ";
		if (qty == 1)
			prompt += "a #b#t" + item + "##k?";
		else
			prompt += "#b" + qty + " #t" + item + "##k?";
        prompt += " Right! I will need some items to make that item. Make sure you have a #bfree slot#k in your inventory!#b";
        if (mats instanceof Array)
            for(var i = 0; i < mats.length; i++)
                prompt += "\r\n#i" + mats[i] + "# " + (matQty[i] * qty) + " #t" + mats[i] + "#";
        else
            prompt += "\r\n#i" + mats + "# " + (matQty * qty) + " #t" + mats + "#";
        if (cost > 0)
            prompt += "\r\n#i4031138# " + (cost * qty) + " meso";
		status = 1;
        cm.sendYesNo(prompt);
	}
}
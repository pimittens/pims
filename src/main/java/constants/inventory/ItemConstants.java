/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
package constants.inventory;

import client.inventory.InventoryType;
import config.YamlConfig;
import constants.id.ItemId;
import server.ItemInformationProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Jay Estrella
 * @author Ronan
 */
public final class ItemConstants {
    protected static Map<Integer, InventoryType> inventoryTypeCache = new HashMap<>();

    public final static short LOCK = 0x01;
    public final static short SPIKES = 0x02;
    public final static short KARMA_USE = 0x02;
    public final static short COLD = 0x04;
    public final static short UNTRADEABLE = 0x08;
    public final static short KARMA_EQP = 0x10;
    public final static short SANDBOX = 0x40;             // let 0x40 until it's proven something uses this
    public final static short PET_COME = 0x80;
    public final static short ACCOUNT_SHARING = 0x100;
    public final static short MERGE_UNTRADEABLE = 0x200;

    public final static boolean EXPIRING_ITEMS = true;
    public final static Set<Integer> permanentItemids = new HashSet<>();

    static {
        // i ain't going to open one gigantic itemid cache just for 4 perma itemids, no way!
        for (int petItemId : ItemId.getPermaPets()) {
            permanentItemids.add(petItemId);
        }
    }

    public static int getFlagByInt(int type) {
        if (type == 128) {
            return PET_COME;
        } else if (type == 256) {
            return ACCOUNT_SHARING;
        }
        return 0;
    }

    public static boolean isThrowingStar(int itemId) {
        return itemId / 10000 == 207;
    }

    public static boolean isBullet(int itemId) {
        return itemId / 10000 == 233;
    }

    public static boolean isPotion(int itemId) {
        return itemId / 1000 == 2000;
    }

    public static boolean isFood(int itemId) {
        int useType = itemId / 1000;
        return useType == 2022 || useType == 2010 || useType == 2020;
    }

    public static boolean isConsumable(int itemId) {
        return isPotion(itemId) || isFood(itemId);
    }

    public static boolean isRechargeable(int itemId) {
        return isThrowingStar(itemId) || isBullet(itemId);
    }

    public static boolean isArrowForCrossBow(int itemId) {
        return itemId / 1000 == 2061;
    }

    public static boolean isArrowForBow(int itemId) {
        return itemId / 1000 == 2060;
    }

    public static boolean isArrow(int itemId) {
        return isArrowForBow(itemId) || isArrowForCrossBow(itemId);
    }

    public static boolean isPet(int itemId) {
        return itemId / 1000 == 5000;
    }

    public static boolean isExpirablePet(int itemId) {
        return YamlConfig.config.server.USE_ERASE_PET_ON_EXPIRATION || itemId == ItemId.PET_SNAIL;
    }

    public static boolean isPermanentItem(int itemId) {
        return permanentItemids.contains(itemId);
    }

    public static boolean isNewYearCardEtc(int itemId) {
        return itemId / 10000 == 430;
    }

    public static boolean isNewYearCardUse(int itemId) {
        return itemId / 10000 == 216;
    }

    public static boolean isAccessory(int itemId) {
        return itemId >= 1110000 && itemId < 1140000;
    }

    public static boolean isTaming(int itemId) {
        int itemType = itemId / 1000;
        return itemType == 1902 || itemType == 1912;
    }

    public static boolean isTownScroll(int itemId) {
        return itemId >= 2030000;
    }

    public static boolean isCleanSlate(int scrollId) {
        return scrollId > 2048999 && scrollId < 2049004;
    }

    public static boolean isModifierScroll(int scrollId) {
        return scrollId == ItemId.SPIKES_SCROLL || scrollId == ItemId.COLD_PROTECTION_SCROLl;
    }

    public static boolean isFlagModifier(int scrollId, short flag) {
        if (scrollId == ItemId.COLD_PROTECTION_SCROLl && ((flag & ItemConstants.COLD) == ItemConstants.COLD)) {
            return true;
        }
        return scrollId == ItemId.SPIKES_SCROLL && ((flag & ItemConstants.SPIKES) == ItemConstants.SPIKES);
    }

    public static boolean isChaosScroll(int scrollId) {
        return scrollId >= 2049100 && scrollId <= 2049103;
    }

    public static boolean isRateCoupon(int itemId) {
        int itemType = itemId / 1000;
        return itemType == 5211 || itemType == 5360;
    }

    public static boolean isExpCoupon(int couponId) {
        return couponId / 1000 == 5211;
    }

    public static boolean isPartyItem(int itemId) {
        return itemId >= 2022430 && itemId <= 2022433 || itemId >= 2022160 && itemId <= 2022163;
    }

    public static boolean isHiredMerchant(int itemId) {
        return itemId / 10000 == 503;
    }

    public static boolean isPlayerShop(int itemId) {
        return itemId / 10000 == 514;
    }

    public static InventoryType getInventoryType(final int itemId) {
        if (inventoryTypeCache.containsKey(itemId)) {
            return inventoryTypeCache.get(itemId);
        }

        InventoryType ret = InventoryType.UNDEFINED;

        final byte type = (byte) (itemId / 1000000);
        if (type >= 1 && type <= 5) {
            ret = InventoryType.getByType(type);
        }

        inventoryTypeCache.put(itemId, ret);
        return ret;
    }

    public static boolean isMakerReagent(int itemId) {
        return itemId / 10000 == 425;
    }

    public static boolean isCap(int itemId) {
        return itemId / 10000 == 100;
    }

    public static boolean isFaceAcc(int itemId) {
        return itemId / 10000 == 101;
    }

    public static boolean isEyeAcc(int itemId) {
        return itemId / 10000 == 102;
    }

    public static boolean isEarring(int itemId) {
        return itemId / 10000 == 103;
    }

    public static boolean isTop(int itemId) {
        return itemId / 10000 == 104;
    }

    public static boolean isOverall(int itemId) {
        return itemId / 10000 == 105;
    }

    public static boolean isBottom(int itemId) {
        return itemId / 10000 == 106;
    }

    public static boolean isShoes(int itemId) {
        return itemId / 10000 == 107;
    }

    public static boolean isShield(int itemId) {
        return itemId / 10000 == 109;
    }

    public static boolean isGlove(int itemId) {
        return itemId / 10000 == 108;
    }

    public static boolean isCape(int itemId) {
        return itemId / 10000 == 110;
    }

    public static boolean isRing(int itemId) {
        return itemId / 10000 == 111;
    }

    public static boolean isPendant(int itemId) {
        return itemId / 10000 == 112;
    }

    public static boolean isBelt(int itemId) {
        return itemId / 10000 == 113;
    }

    public static boolean is1hSword(int itemId) {
        return itemId / 10000 == 130;
    }

    public static boolean is1hAxe(int itemId) {
        return itemId / 10000 == 131;
    }

    public static boolean is1hBluntWeapon(int itemId) {
        return itemId / 10000 == 132;
    }

    public static boolean isDagger(int itemId) {
        return itemId / 10000 == 133;
    }

    public static boolean isWand(int itemId) {
        return itemId / 10000 == 137;
    }

    public static boolean isStaff(int itemId) {
        return itemId / 10000 == 138;
    }

    public static boolean is2hSword(int itemId) {
        return itemId / 10000 == 140;
    }

    public static boolean is2hAxe(int itemId) {
        return itemId / 10000 == 141;
    }

    public static boolean is2hBluntWeapon(int itemId) {
        return itemId / 10000 == 142;
    }

    public static boolean isSpear(int itemId) {
        return itemId / 10000 == 143;
    }

    public static boolean isPolearm(int itemId) {
        return itemId / 10000 == 144;
    }

    public static boolean isBow(int itemId) {
        return itemId / 10000 == 145;
    }

    public static boolean isCrossbow(int itemId) {
        return itemId / 10000 == 146;
    }

    public static boolean isClaw(int itemId) {
        return itemId / 10000 == 147;
    }

    public static boolean isKnuckle(int itemId) {
        return itemId / 10000 == 148;
    }

    public static boolean isGun(int itemId) {
        return itemId / 10000 == 149;
    }

    public static boolean is1hWeapon(int itemId) {
        return is1hSword(itemId) || is1hAxe(itemId) || is1hBluntWeapon(itemId) || isWand(itemId) || isStaff(itemId) || isDagger(itemId);
    }

    public static boolean is2hWeapon(int itemId) {
        return isWeapon(itemId) && !is1hWeapon(itemId);
    }

    public static short getEquipSlot(int itemId) {
        if (isCap(itemId)) {
            return -1;
        }
        if (isFaceAcc(itemId)) {
            return -2;
        }
        if (isEyeAcc(itemId)) {
            return -3;
        }
        if (isEarring(itemId)) {
            return -4;
        }
        if (isOverall(itemId) || isTop(itemId)) {
            return -5;
        }
        if (isBottom(itemId)) {
            return -6;
        }
        if (isShoes(itemId)) {
            return -7;
        }
        if (isGlove(itemId)) {
            return -8;
        }
        if (isCape(itemId)) {
            return -9;
        }
        if (isShield(itemId)) {
            return -10;
        }
        if (isWeapon(itemId)) {
            return -11;
        }
        if (isRing(itemId)) {
            return -12; // note: this is the first ring slot, they can also go in -13, -15, and -16
        }
        if (isPendant(itemId)) {
            return -17;
        }
        if (isMedal(itemId)) {
            return -49;
        }
        if (isBelt(itemId)) {
            return -50;
        }
        return 0;
    }

    public static int getItemValue(int itemId, int quantity) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        return ii.getPrice(itemId, quantity);
    }

    public static boolean isCashStore(int itemId) {
        int itemType = itemId / 10000;
        return itemType == 503 || itemType == 514;
    }

    public static boolean isMapleLife(int itemId) {
        int itemType = itemId / 10000;
        return itemType == 543 && itemId != 5430000;
    }

    public static boolean isWeapon(int itemId) {
        return itemId >= 1302000 && itemId < 1493000;
    }

    public static boolean isEquipment(int itemId) {
        return itemId < 2000000 && itemId != 0;
    }

    public static boolean isFishingChair(int itemId) {
        return itemId == ItemId.FISHING_CHAIR;
    }

    public static boolean isMedal(int itemId) {
        return itemId >= 1140000 && itemId < 1143000;
    }

    public static boolean isFace(int itemId) {
        return itemId >= 20000 && itemId < 22000;
    }

    public static boolean isHair(int itemId) {
        return itemId >= 30000 && itemId < 35000;
    }
}

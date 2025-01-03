USE `cosmic`;

CREATE TABLE IF NOT EXISTS `marketequipment` (
  `marketequipmentid` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `marketitemid` int(10) unsigned NOT NULL DEFAULT '0',
  `upgradeslots` int(11) NOT NULL DEFAULT '0',
  `level` int(11) NOT NULL DEFAULT '0',
  `str` int(11) NOT NULL DEFAULT '0',
  `dex` int(11) NOT NULL DEFAULT '0',
  `int` int(11) NOT NULL DEFAULT '0',
  `luk` int(11) NOT NULL DEFAULT '0',
  `hp` int(11) NOT NULL DEFAULT '0',
  `mp` int(11) NOT NULL DEFAULT '0',
  `watk` int(11) NOT NULL DEFAULT '0',
  `matk` int(11) NOT NULL DEFAULT '0',
  `wdef` int(11) NOT NULL DEFAULT '0',
  `mdef` int(11) NOT NULL DEFAULT '0',
  `acc` int(11) NOT NULL DEFAULT '0',
  `avoid` int(11) NOT NULL DEFAULT '0',
  `hands` int(11) NOT NULL DEFAULT '0',
  `speed` int(11) NOT NULL DEFAULT '0',
  `jump` int(11) NOT NULL DEFAULT '0',
  `locked` int(11) NOT NULL DEFAULT '0',
  `vicious` int(11) unsigned NOT NULL DEFAULT '0',
  `itemlevel` int(11) NOT NULL DEFAULT '1',
  `itemexp` int(11) unsigned NOT NULL DEFAULT '0',
  `ringid` int(11) NOT NULL DEFAULT '-1',
  PRIMARY KEY (`marketequipmentid`),
  KEY `MARKETITEMID` (`marketitemid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1 ;

CREATE TABLE IF NOT EXISTS `marketitems` (
  `marketitemid` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `itemid` int(11) NOT NULL DEFAULT '0',
  `quantity` int(11) NOT NULL DEFAULT '0',
  `marketPrice` int(11) NOT NULL DEFAULT '0',
  `averageSalePrice` int(11) NOT NULL DEFAULT '0',
  `numSold` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`marketitemid`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1 AUTO_INCREMENT=1 ;



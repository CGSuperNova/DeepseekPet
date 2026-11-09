* %cmi_user_charges_left% – Remaining count of spawner charges
* %cmi_user_charges_max% – Maximum allowed amount of spawner charges
* %cmi_user_charges_time% – Time until next spawner charge
* %cmi_user_charges_cooldown% – Spawner charge cooldown
* %cmi_user_display_name% – Formatted players display name
* %cmi_user_name% – Original players name
* %cmi_user_uuid% – Player uuid
* %cmi_user_deathloc% – Players last death location
* %cmi_user_backloc% – Players back location
* %cmi_user_cuffed% – Identification if player is cuffed
* %cmi_user_muted% – Identification if player is muted
* %cmi_user_inpvp% – Identification if player is in pvp mode
* %cmi_user_god% – Identification if player has god mode enabled
* %cmi_user_sneaking% – Identification if player is sneaking
* %cmi_user_mail_count% – Amount of mails player have
* %cmi_user_warning_count% – Amount of warnings player have
* %cmi_user_warning_points% – Amount of warning points player have
* %cmi_user_afk% – Afk state
* %cmi_user_afk_symbol% – Afk symbol
* %cmi_user_afk_msg% – Afk message if present
* %cmi_user_afk_for% – Time for how long player is in afk mode
* %cmi_user_afk_in% – Time when player enters auto afk mode
* %cmi_user_joinedcounter% – Indication if player is joined counter
* %cmi_user_banned% – Indication if player is banned
* %cmi_user_missingexp% – Missing exp amount until next level
* %cmi_user_missingexpp% – Missing exp in percentage until next level
* %cmi_user_exp% – Current exp amount for current level
* %cmi_user_expp% – Current exp in percentage for current level
* %cmi_user_totalexp% – Total amount of exp player have
* %cmi_user_level% – Players level
* %cmi_user_ping% – Ping
* %cmi_user_gamemode% – Game mode
* %cmi_user_op% – OP state
* %cmi_user_pweather% – Player weather
* %cmi_user_weather% – Weather at players world
* %cmi_user_weatherduration% – Weather duration at players world
* %cmi_user_canfly% – Players ability to fly
* %cmi_user_flying% – Is player currently flying
* %cmi_user_balance_formatted% – Formatted users balance
* %cmi_user_balance% – Clean users balance
* %cmi_user_tfly% – Left temp fly amount in seconds
* %cmi_user_tfly_formated%
* %cmi_user_tfly_formatted% – Formatted temp fly amount
* %cmi_user_flightcharge% – Flight charge amount （服务器内译名叫飞行能量）
* %cmi_user_country% – Users country from geoip feature. Example: Germany
* %cmi_user_country_code% – Users country code from geoip feature. Example: UK
* %cmi_user_city% – Users city name from geoip feature
* %cmi_user_riding% – Entity name player is riding
* %cmi_user_beingriddenby% – Player name who is riding user
* %cmi_user_playtime_formatted% – Formatted playtime
* %cmi_user_playtime_days% – Playtime in days
* %cmi_user_playtime_dayst% – Playtime in days with fraction
* %cmi_user_playtime_hours% – Playtime in hours
* %cmi_user_playtime_hoursf% – Total playtime in hours
* %cmi_user_playtime_hourst% – Total playtime in hours with fraction
* %cmi_user_playtime_minutes% – Playtime in minutes
* %cmi_user_playtime_minutest% – Total playtime in minutes
* %cmi_user_playtime_seconds% – Playtime in minutes
* %cmi_user_playtime_secondst% – Total playtime in minutes
* %cmi_user_world_formatted% – Current players world name by using custom identification
* %cmi_user_online% – Returns player online status
* %cmi_user_itemcount_[itemIdName(:data)]% – Number of items in players inventory by provided material
* %cmi_iteminhand_displayname% – Items in main hand display name or formatted material name
* %cmi_iteminhand_realname% – Items in main hand formatted material name
* %cmi_iteminhand_type% – Items in main hand material name
* %cmi_iteminhand_itemdata% – Items in main hand data value. As of 1.13+ returns 0
* %cmi_iteminhand_amount% – Amount of items in main hand
* %cmi_iteminhand_durability% – Items in main hand left durability
* %cmi_iteminhand_maxdurability% – Items in main hand max durability
* %cmi_iteminhand_custommodeldata% – Items in main hand custom model data
* %cmi_iteminhand_worth% – Returns total worth value of items in main hand
* %cmi_iteminhand_worth_one% – Returns worth value of one item from main hand
* %cmi_iteminhand_worthc% – Returns total worth value of items in main hand without formatting
* %cmi_iteminhand_worthc_one% – Returns worth value of one item from main hand without formatting
* %cmi_material_realname_$1% – Items in main hand formatted material name
* %cmi_baltop_cname_[1-10]% – Clean name of player from a provided place in a list
* %cmi_baltop_name_[1-10]% – Name of player from a provided place in a list
* %cmi_baltop_money_[1-10]% – Balance of player from a provided place in a list
* %cmi_baltop_shortmoney_[1-10]% – Balance of player from a provided place in a list
* %cmi_playtimetop_cname_[1-10]% – Clean name of player from a provided place in a list
* %cmi_playtimetop_name_[1-10]% – Name of player from a provided place in a list
* %cmi_playtimetop_time_[1-10]% – Playtime of player from a provided place in a list
* %cmi_worth_buy_[itemIdName(:data)]% – Value of the item
* %cmi_worth_sell_[itemIdName(:data)]% – Sell value of the item
* %cmi_worthc_buy_[itemIdName(:data)]% – Value of the item without formatting
* %cmi_worthc_sell_[itemIdName(:data)]% – Sell value of the item without formatting
* %cmi_tps_1% – Ttps from last 1 second
* %cmi_tps_60% – Tps from last 60 seconds
* %cmi_tps_300% – Tps from last 5 minutes
* %cmi_user_meta_[key]% – Players metadate by defined key
* %cmi_user_metaint_[key]% – Players metadate by defined key as number
* * 补充：该变量需要在DeepseekPet插件配置中添加一个专门的配置项来编写有哪些key
* %cmi_chatmute_time% – Provides left time for global chat mute
* %cmi_chatmute_reason% – Provides reason for global chat mute
* %cmi_user_baltop% – Player position in baltop
* %cmi_user_playtimetop% – Player position in playtimetop
* %cmi_user_stats_[mainStat(:optionalSubStat)]% – Player statistics
* %cmi_weather_[worldName]% – World weather
* %cmi_weatherduration_[worldName]% – World weather duration
* %cmi_afk_count% – Number of afk players
* %player_x% – Current players x position
* %cmi_player_x% – Current players x position
* %player_y% – Current players y position
* %cmi_player_y% – Current players y position
* %player_z% – Current players z position
* %cmi_player_z% – Current players z position
* %player_biome% – Current players biome name
* %cmi_player_biome% – Current players biome name
* %vault_eco_balance_formatted% – Formatted players balance. Deprecated
* * 补充：玩家金币变量直接用 %vault_eco_balance%
* %server_online% – Online player amount
* %cmi_server_online% – Online player count
* %server_time_[timeFormat]% – Local server time
* %cmi_server_time_[timeFormat]% – Local server time
* %world_time12_[worldName]% – World time in 12 hour format
* %cmi_world_time12_[worldName]% – World time in 12 hour format
* %world_time24_[worldName]% – World time in 24 hour format
* %cmi_world_time24_[worldName]% – World time in 24 hour format
// 其他代码保持不变...

private void initializeCheckinInventory() {
    int size = config.getInt("menu-size", 9);
    if (size % 9 != 0 || size < 9 || size > 54) {
        size = 9;
    }
    
    String title = ChatColor.translateAlternateColorCodes('&', 
            config.getString("menu-title", "&6签到系统"));
    checkinInventory = Bukkit.createInventory(null, size, title);
    
    // 设置菜单背景
    ItemStack background = parseItemStack(config.getNode("menu-background"));
    if (background != null) {
        for (int i = 0; i < size; i++) {
            checkinInventory.setItem(i, background);
        }
    }
    
    // 设置分隔线
    String dividerSlotRange = config.getString("divider-slot", "");
    if (!dividerSlotRange.isEmpty()) {
        ItemStack divider = parseItemStack(config.getNode("divider-item"));
        if (divider != null) {
            String[] parts = dividerSlotRange.split("-");
            if (parts.length == 2) {
                try {
                    int start = Integer.parseInt(parts[0]);
                    int end = Integer.parseInt(parts[1]);
                    for (int i = start; i <= end && i < size; i++) {
                        checkinInventory.setItem(i, divider);
                    }
                } catch (NumberFormatException e) {
                    getLogger().warning("无效的分隔线插槽配置: " + dividerSlotRange);
                }
            }
        }
    }
    
    // 设置奖励预览
    int rewardPreviewSlot = config.getInt("reward-preview-slot", -1);
    if (rewardPreviewSlot >= 0 && rewardPreviewSlot < size) {
        ItemStack rewardPreviewItem = parseItemStack(config.getNode("reward-preview-item"));
        if (rewardPreviewItem != null) {
            checkinInventory.setItem(rewardPreviewSlot, rewardPreviewItem);
        }
    }
    
    // 设置签到按钮
    int checkinButtonSlot = config.getInt("checkin-button-slot", size - 5);
    if (checkinButtonSlot >= 0 && checkinButtonSlot < size) {
        ItemStack checkinButton = parseItemStack(config.getNode("checkin-button"));
        if (checkinButton != null) {
            checkinInventory.setItem(checkinButtonSlot, checkinButton);
        }
    }
    
    // 设置统计信息按钮
    int statsSlot = config.getInt("stats-slot", -1);
    if (statsSlot >= 0 && statsSlot < size) {
        ItemStack statsItem = parseItemStack(config.getNode("stats-item"));
        if (statsItem != null) {
            checkinInventory.setItem(statsSlot, statsItem);
        }
    }
}

// 其他代码保持不变...

private void processCheckin(Player player) {
    CheckinData data = checkinDataMap.get(player.getUniqueId());
    if (data == null) {
        loadPlayerData(player.getUniqueId());
        data = checkinDataMap.get(player.getUniqueId());
    }
    
    if (data == null) {
        player.sendMessage(ChatColor.RED + "签到失败！无法加载您的签到数据。");
        return;
    }
    
    String lastCheckinDate = data.getLastCheckinDate();
    LocalDate currentDate = LocalDate.now();
    LocalDate yesterday = currentDate.minusDays(1);
    
    try {
        LocalDate lastDate = lastCheckinDate.isEmpty() ? null : 
                LocalDate.parse(lastCheckinDate, DateTimeFormatter.ISO_LOCAL_DATE);
        
        // 更新连续签到天数
        int newStreak = 1;
        if (lastDate != null && lastDate.isEqual(yesterday)) {
            // 连续签到
            newStreak = data.getStreak() + 1;
        }
        
        data.setStreak(newStreak);
        
        // 更新总签到次数
        data.setTotalCheckins(data.getTotalCheckins() + 1);
        
        // 更新最后签到日期
        data.setLastCheckinDate(currentDate.toString());
        
        // 发放固定10金币奖励
        giveMoney(player, 10.0);
        player.sendMessage(ChatColor.GREEN + "获得 10 " + currencyName + "！");
        
        // 记录已领取的奖励
        data.getClaimedRewards().add(currentDate.toString());
        
        player.sendMessage(ChatColor.GREEN + "签到成功！");
        
        // 播放音效
        String successSound = config.getString("sounds.checkin-success", "ENTITY_PLAYER_LEVELUP");
        try {
            Sound sound = Sound.valueOf(successSound);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            getLogger().warning("无效的音效配置: " + successSound);
        }
        
        // 保存数据
        savePlayerData(player.getUniqueId());
        
    } catch (Exception e) {
        player.sendMessage(ChatColor.RED + "签到处理过程中发生错误！");
        getLogger().severe("处理玩家签到时出错: " + player.getName());
        e.printStackTrace();
    }
}

// 其他代码保持不变...

private void openCheckinMenu(Player player) {
    // 检查冷却时间
    long currentTime = System.currentTimeMillis();
    if (cooldownMap.containsKey(player.getUniqueId()) && 
        currentTime - cooldownMap.get(player.getUniqueId()) < cooldownTime) {
        return;
    }
    cooldownMap.put(player.getUniqueId(), currentTime);
    
    CheckinData data = checkinDataMap.get(player.getUniqueId());
    if (data == null) {
        loadPlayerData(player.getUniqueId());
        data = checkinDataMap.get(player.getUniqueId());
    }
    
    if (data == null) {
        player.sendMessage(ChatColor.RED + "无法加载您的签到数据！");
        return;
    }
    
    // 创建临时菜单副本
    Inventory tempInventory = Bukkit.createInventory(null, checkinInventory.getSize(), 
            checkinInventory.getTitle());
    
    // 复制菜单内容
    for (int i = 0; i < checkinInventory.getSize(); i++) {
        ItemStack original = checkinInventory.getItem(i);
        if (original != null) {
            tempInventory.setItem(i, original.clone());
        }
    }
    
    // 更新签到按钮状态
    int checkinButtonSlot = config.getInt("checkin-button-slot", checkinInventory.getSize() - 5);
    if (checkinButtonSlot >= 0 && checkinButtonSlot < checkinInventory.getSize()) {
        ItemStack checkinButton = tempInventory.getItem(checkinButtonSlot);
        if (checkinButton != null) {
            ItemMeta meta = checkinButton.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                
                // 清除原有lore
                lore.clear();
                
                // 获取上次签到日期
                String lastCheckinDate = data.getLastCheckinDate().isEmpty() ? 
                        "从未签到" : data.getLastCheckinDate();
                
                // 添加新lore
                lore.add(ChatColor.GRAY + "连续签到: " + data.getStreak() + " 天");
                lore.add(ChatColor.GRAY + "总签到次数: " + data.getTotalCheckins() + " 次");
                lore.add("");
                
                if (canCheckin(player)) {
                    lore.add(ChatColor.GREEN + "点击此处签到");
                    lore.add(ChatColor.YELLOW + "今日奖励: 10 金币");
                } else {
                    lore.add(ChatColor.RED + "今日已签到");
                    lore.add(ChatColor.GRAY + "下次签到时间: 明天");
                }
                lore.add("");
                lore.add(ChatColor.GRAY + "上次签到: " + lastCheckinDate);
                
                meta.setLore(lore);
                checkinButton.setItemMeta(meta);
                tempInventory.setItem(checkinButtonSlot, checkinButton);
            }
        }
    }
    
    // 更新统计信息
    int statsSlot = config.getInt("stats-slot", -1);
    if (statsSlot >= 0 && statsSlot < tempInventory.getSize()) {
        ItemStack statsItem = tempInventory.getItem(statsSlot);
        if (statsItem != null) {
            ItemMeta meta = statsItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                
                // 计算本月签到天数
                int monthlyCheckins = 0;
                LocalDate currentDate = LocalDate.now();
                String currentMonth = currentDate.getYear() + "-" + currentDate.getMonthValue();
                
                for (String reward : data.getClaimedRewards()) {
                    if (reward.startsWith(currentMonth)) {
                        monthlyCheckins++;
                    }
                }
                
                // 计算累计获得金币
                int totalMoney = data.getTotalCheckins() * 10;
                
                // 清除原有lore
                lore.clear();
                
                // 添加新lore
                lore.add(ChatColor.GRAY + "本月签到: " + monthlyCheckins + " 天");
                lore.add(ChatColor.GRAY + "连续签到: " + data.getStreak() + " 天");
                lore.add(ChatColor.GRAY + "总签到次数: " + data.getTotalCheckins() + " 次");
                lore.add(ChatColor.GRAY + "累计获得: " + totalMoney + " 金币");
                
                meta.setLore(lore);
                statsItem.setItemMeta(meta);
                tempInventory.setItem(statsSlot, statsItem);
            }
        }
    }
    
    // 播放打开菜单音效
    String openMenuSound = config.getString("sounds.open-menu", "UI_BUTTON_CLICK");
    try {
        Sound sound = Sound.valueOf(openMenuSound);
        player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
    } catch (IllegalArgumentException e) {
        getLogger().warning("无效的音效配置: " + openMenuSound);
    }
    
    // 打开菜单
    player.openInventory(tempInventory);
}

// 其他代码保持不变...    
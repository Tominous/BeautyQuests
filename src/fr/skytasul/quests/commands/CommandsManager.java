	package fr.skytasul.quests.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import fr.skytasul.quests.Quest;
import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.utils.DebugUtils;
import fr.skytasul.quests.utils.Lang;
import fr.skytasul.quests.utils.Utils;
import fr.skytasul.quests.utils.types.Pair;
import fr.skytasul.quests.utils.types.RunnableObj;

public class CommandsManager implements CommandExecutor, TabCompleter{

	public final Map<String, Pair<Cmd, Method>> commands = new HashMap<>();
	private RunnableObj noArgs;
	private Object commandsClass;
	
	/**
	 * @param noArgs RunnableObj(player) who'll be ran if the command is executed without any arguments <i>(can be null)</i>
	 */
	public CommandsManager(RunnableObj noArgs){
		this.noArgs = noArgs;
		this.commandsClass = commands;
	}
	
	/**
	 * Register all available commands from an instance of a Class
	 */
	public void registerCommandsClass(Object commandsClassInstance){
		for(Method method : commandsClassInstance.getClass().getDeclaredMethods()){
			if (method.isAnnotationPresent(Cmd.class)){
				Cmd cmd = method.getDeclaredAnnotation(Cmd.class);
				if (method.getParameterCount() == 1){
					if (method.getParameterTypes()[0] == CommandContext[].class){
						this.commands.put(method.getName().toLowerCase(), new Pair<Cmd, Method>(cmd, method));
						continue;
					}
				}
				DebugUtils.logMessage("Error when loading command annotated method " + method.getName() + " in class " + commandsClassInstance.getClass().getName() + ". Needed argument: fr.skytasul.quests.commands.CommandContext");
			}
		}
	}
	
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if (args.length == 0){
			if (noArgs != null){
				noArgs.run(sender);
			}else Lang.INCORRECT_SYNTAX.sendWP(sender);
			return false;
		}
		
		Pair<Cmd, Method> pair = commands.get(args[0].toLowerCase());
		if (pair == null){
			Lang.COMMAND_DOESNT_EXIST.sendWP(sender);
			return false;
		}
		
		Cmd cmd = pair.getKey();
		if (cmd.player() && !(sender instanceof Player)){
			Lang.MUST_PLAYER.sendWP(sender);
			return false;
		}
		
		if (!cmd.permission().isEmpty() && !hasPermission(sender, cmd.permission())){
			Lang.PERMISSION_REQUIRED.sendWP(sender, cmd.permission());
			return false;
		}
		
		if (args.length - 1 < cmd.min()){
			Lang.INCORRECT_SYNTAX.sendWP(sender);
			return false;
		}
		
		Object[] argsCmd = new Object[args.length - 1];
		for (int i = 1; i < args.length; i++){
			String arg = args[i];
			String type = cmd.args()[i];
			if (type.equals("PLAYERS")){
				Player target = Bukkit.getPlayerExact(arg);
				if (target == null){
					Lang.PLAYER_NOT_ONLINE.send(sender, arg);
					return false;
				}
				argsCmd[i-1] = target;
			}else if (type.equals("QUESTSID")){
				Integer id = Utils.parseInt(sender, arg);
				if (id == null) return false;
				Quest qu = QuestsAPI.getQuestFromID(id);
				if (qu == null){
					Lang.QUEST_INVALID.send(sender, id);
					return false;
				}
				argsCmd[i-1] = qu;
			}else {
				argsCmd[i-1] = arg;
			}
		}
		
		try {
			pair.getValue().invoke(commandsClass, new CommandContext(this, sender, argsCmd, label));
		}catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			Lang.ERROR_OCCURED.send(sender, " command executing");
			e.printStackTrace();
		}
		
		return false;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args){
		List<String> tmp = new ArrayList<>();
		List<String> find = new ArrayList<>();
		String sel = args[0];
		
		if (args.length == 1){
			for (Entry<String, Pair<Cmd, Method>> en : commands.entrySet()){ // PERMISSIONS
				String perm = en.getValue().getKey().permission();
				if (perm != null && sender.hasPermission(perm)) find.add(en.getKey());
			}
		}else if (args.length >= 2){
			int index = args.length-2;
			if (!commands.containsKey(sel)) return tmp;
			Pair<Cmd, Method> pair = commands.get(sel);
			String[] needed = pair.getKey().args();
			if (needed.length <= index) return tmp;
			if (!pair.getKey().permission().isEmpty() && !hasPermission(sender, pair.getKey().permission())) return tmp;
			sel = args[index + 1];
			String key = needed[index];
			if (key.equals("QUESTSID")){
				for (Quest quest : QuestsAPI.getQuests()) find.add(quest.getID() + "");
			}else if (key.equals("PLAYERS")){
				return null;
			}else{
				find = new ArrayList<>(Arrays.asList(key.split("\\|")));
			}
		}else return tmp;
		
		for (String arg : find){
			if (arg.startsWith(sel)) tmp.add(arg);
		}
		return tmp;
	}
	
	public static boolean hasPermission(CommandSender sender, String cmd){
		return sender.hasPermission(("beautyquests.command." + cmd));
	}
	
}
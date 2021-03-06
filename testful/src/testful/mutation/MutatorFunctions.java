package testful.mutation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.IntegerType;
import soot.Local;
import soot.LongType;
import soot.PrimType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.VoidType;
import soot.jimple.AddExpr;
import soot.jimple.AndExpr;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.ConcreteRef;
import soot.jimple.ConditionExpr;
import soot.jimple.DivExpr;
import soot.jimple.DoubleConstant;
import soot.jimple.EqExpr;
import soot.jimple.FieldRef;
import soot.jimple.FloatConstant;
import soot.jimple.GeExpr;
import soot.jimple.GtExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceOfExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LeExpr;
import soot.jimple.LongConstant;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.LtExpr;
import soot.jimple.MulExpr;
import soot.jimple.NeExpr;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NopStmt;
import soot.jimple.NullConstant;
import soot.jimple.NumericConstant;
import soot.jimple.OrExpr;
import soot.jimple.RemExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.SubExpr;
import soot.jimple.TableSwitchStmt;
import soot.jimple.UnopExpr;
import soot.jimple.XorExpr;
import soot.tagkit.StringTag;
import soot.util.Chain;
import testful.IConfigProject;
import testful.coverage.soot.Instrumenter.UnifiedInstrumentator;
import testful.coverage.soot.Skip;

public class MutatorFunctions implements UnifiedInstrumentator {

	/** reference to the config class (see <code>ConfigHandler.CONFIG_CLASS</config>) */
	private static final SootClass config;

	/** reference to Math.abs(int) */
	private static final SootMethod mathAbsInt;
	/** reference to Math.abs(long) */
	private static final SootMethod mathAbsLong;
	/** reference to Math.abs(float) */
	private static final SootMethod mathAbsFloat;
	/** reference to Math.abs(double) */
	private static final SootMethod mathAbsDouble;

	/** reference to Utils.zpush(int) */
	private final static SootMethod absZpushInt;
	/** reference to Utils.zpush(long) */
	private final static SootMethod absZpushLong;
	/** reference to Utils.zpush(float) */
	private final static SootMethod absZpushFloat;
	/** reference to Utils.zpush(double) */
	private final static SootMethod absZpushDouble;

	/** reference to BitSet */
	private final static SootClass bitSet;
	/** reference to BitSet.set(int) */
	private final static SootMethod bitSet_set;
	private final static SootMethod bitSet_constructor;

	static {
		Scene.v().loadClassAndSupport(Utils.CONFIG_CLASS);
		config = Scene.v().getSootClass(Utils.CONFIG_CLASS);

		Scene.v().loadClassAndSupport(Utils.class.getName());
		SootClass utils = Scene.v().getSootClass(Utils.class.getName());
		absZpushInt = utils.getMethod("zpush", Arrays.asList(new Object[] { IntType.v() }));
		absZpushLong = utils.getMethod("zpush", Arrays.asList(new Object[] { LongType.v() }));
		absZpushFloat = utils.getMethod("zpush", Arrays.asList(new Object[] { FloatType.v() }));
		absZpushDouble = utils.getMethod("zpush", Arrays.asList(new Object[] { DoubleType.v() }));

		Scene.v().loadClassAndSupport(Math.class.getName());
		SootClass mathAbs = Scene.v().getSootClass(Math.class.getName());
		mathAbsInt = mathAbs.getMethod("abs", Arrays.asList(new Object[] { IntType.v() }));
		mathAbsLong = mathAbs.getMethod("abs", Arrays.asList(new Object[] { LongType.v() }));
		mathAbsFloat = mathAbs.getMethod("abs", Arrays.asList(new Object[] { FloatType.v() }));
		mathAbsDouble = mathAbs.getMethod("abs", Arrays.asList(new Object[] { DoubleType.v() }));

		Scene.v().loadClassAndSupport(BitSet.class.getName());
		bitSet = Scene.v().getSootClass(BitSet.class.getName());
		bitSet_set = bitSet.getMethod("set", Arrays.asList(new Object[] { IntType.v() }));
		bitSet_constructor = bitSet.getMethod(SootMethod.constructorName, new ArrayList<Object>());
	}

	private final ConfigMutation configMutation;
	MutatorFunctions(ConfigMutation configMutation) {
		this.configMutation = configMutation;

		if(configMutation.isAbs()) System.out.println("  ABS mutation function enabled");
		if(configMutation.isAor()) System.out.println("  AOR mutation function enabled");
		if(configMutation.isLcr()) System.out.println("  LCR mutation function enabled");
		if(configMutation.isRor()) System.out.println("  ROR mutation function enabled");
		if(configMutation.isUoi()) System.out.println("  UOI mutation function enabled");
		if(configMutation.isTrack()) System.out.println("  Track execution of mutants enabled");
	}


	@Override
	public void preprocess(SootClass sClass) {
		ConfigHandler.singleton.manage(sClass);
	}

	private SootClass sClass;
	private SootMethod method;
	private Local liveMutants;
	private Local lCurrentMutation;
	private Map<Type, Local> tempLocals;

	/* (non-Javadoc)
	 * @see testful.coverage.soot.Instrumenter.UnifiedInstrumentator#init(soot.Body, soot.Body, soot.util.Chain)
	 */
	@Override
	public void init(Body oldBody, Body newBody, Chain<Unit> newUnits, IdentityStmt[] paramDefs) {
		method = oldBody.getMethod();
		sClass = method.getDeclaringClass();
		final String className = sClass.getName();

		tempLocals = new HashMap<Type, Local>();
		tempLocals.put(BooleanType.v(), Jimple.v().newLocal("__mutation_temp_bool__", BooleanType.v()));
		tempLocals.put(ByteType.v(), Jimple.v().newLocal("__mutation_temp_byte__", ByteType.v()));
		tempLocals.put(IntType.v(), Jimple.v().newLocal("__mutation_temp_int__", IntType.v()));
		tempLocals.put(LongType.v(), Jimple.v().newLocal("__mutation_temp_long__", LongType.v()));
		tempLocals.put(FloatType.v(), Jimple.v().newLocal("__mutation_temp_float__", FloatType.v()));
		tempLocals.put(DoubleType.v(), Jimple.v().newLocal("__mutation_temp_double__", DoubleType.v()));
		newBody.getLocals().addAll(tempLocals.values());

		// read the current mutation
		lCurrentMutation = Jimple.v().newLocal("__selected_mutation__", IntType.v());
		newBody.getLocals().add(lCurrentMutation);
		SootField curField = config.getFieldByName(Utils.getCurField(className));
		newUnits.add(Jimple.v().newAssignStmt(lCurrentMutation, Jimple.v().newStaticFieldRef(curField.makeRef())));

		// create a reference to the bitset of live mutants
		if(configMutation.isTrack()) {
			liveMutants = Jimple.v().newLocal("__live_mutants__", bitSet.getType());
			newBody.getLocals().add(liveMutants);
			newUnits.add(Jimple.v().newAssignStmt(liveMutants, Jimple.v().newStaticFieldRef(sClass.getFieldByName(Utils.EXECUTED_MUTANTS).makeRef())));

			NopStmt nop = Jimple.v().newNopStmt();

			// if mut != -1 goto nop
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(lCurrentMutation, IntConstant.v(-1)), nop));

			// if liveMutants != null goto nop
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(liveMutants, NullConstant.v()), nop));

			// liveMutants = new java.util.BitSet
			newUnits.add(Jimple.v().newAssignStmt(liveMutants, Jimple.v().newNewExpr(bitSet.getType())));
			//specialinvoke liveMutants.<java.util.BitSet: void <init>()>()
			InvokeStmt stmt = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(liveMutants, bitSet_constructor.makeRef()));
			stmt.addTag(Skip.s);
			newUnits.add(stmt);
			// this.liveMutants = liveMutants
			newUnits.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(sClass.getFieldByName(Utils.EXECUTED_MUTANTS).makeRef()), liveMutants));

			newUnits.add(nop);
		} else {
			liveMutants = null;
		}

	}

	private Unit nop;

	@Override
	public void processPre(Chain<Unit> newUnits, Stmt stmt) {
		if(tempLocals == null) return;

		Set<Integer> mutations = new HashSet<Integer>();

		nop = Jimple.v().newNopStmt();
		nop.addTag(new StringTag("afterOp"));


		if(stmt instanceof AssignStmt) {
			AssignStmt assign = (AssignStmt) stmt;
			Type type = assign.getLeftOp().getType();

			// skip assignments to fields:
			// the operation:      field = a+b
			// in jimple becomes:  $tmp = a+b; field = $tmp
			if(!(assign.getLeftOp() instanceof FieldRef)) // apply mutator functions working on numbers
				if(type instanceof PrimType && // only for performance!
						((type instanceof IntegerType && !(type instanceof BooleanType)) || type instanceof LongType || type instanceof FloatType || type instanceof DoubleType)) {

					// ABS
					if(configMutation.isAbs()) abs(sClass, method, assign, mutations, newUnits, lCurrentMutation, nop);

					// AOR
					if(configMutation.isAor()) aor(sClass, method, assign, mutations, newUnits, lCurrentMutation, nop);

				}

			// apply mutator functions working on booleans
				else if(type instanceof BooleanType) {
					// LCR
					if(configMutation.isLcr()) lcr(sClass, method, assign, mutations, newUnits, lCurrentMutation, nop);

					// ROR
					if(configMutation.isRor()) ror(sClass, method, assign, mutations, newUnits, lCurrentMutation, nop);
				}
		} else if(stmt instanceof IfStmt) {
			IfStmt ifStmt = (IfStmt) stmt;

			// sometimes the operatins with boolean values are
			// converted into a set of if statement

			// WARN: cannot easily apply LCR!

			// ROR
			if(configMutation.isRor()) ror(sClass, method, ifStmt, mutations, newUnits, lCurrentMutation, nop);
		}

		if(configMutation.isUoi()) uoi(sClass, method, stmt, mutations, newUnits, lCurrentMutation, nop);

		if(configMutation.isTrack()) track(mutations, newUnits, lCurrentMutation, liveMutants);

	}

	@Override
	public void processPost(Chain<Unit> newUnits, Stmt op) {
		if(nop != null) {
			newUnits.add(nop);
			nop = null;
		}
	}

	@Override
	public void exceptional(Chain<Unit> newUnits, Local exc) { }

	@Override
	public void done(IConfigProject config) {
		try {
			ConfigHandler.singleton.done(config.getDirInstrumented());

			PrintStream statFile = new PrintStream(new File(config.getDirInstrumented(),  "mutants.txt"));
			ConfigHandler.singleton.writeStats(statFile);
			statFile.close();
		} catch(IOException e) {
			System.err.println("Cannot write stats: " + e);
		}
	}

	private void track(Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Local executedMutants) {
		if(!mutations.isEmpty()) {
			// if selMutation == -1, report that the active set of mutants is "mutations"
			NopStmt nop = Jimple.v().newNopStmt();
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(-1)), nop));

			for(Integer m : mutations) {
				InvokeStmt stmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(executedMutants, bitSet_set.makeRef(), IntConstant.v(m)));
				stmt.addTag(Skip.s);
				newUnits.add(stmt);
			}

			newUnits.add(nop);
			nop.addTag(new StringTag("end of TRACK_EXECUTED_MUTANTS"));
		}
	}

	private void aor(SootClass sClass, SootMethod meth, AssignStmt assign, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {
		if(!(assign.getRightOp() instanceof BinopExpr)) return;

		BinopExpr binOp = (BinopExpr) assign.getRightOp();

		if(!(binOp instanceof AddExpr || binOp instanceof DivExpr || binOp instanceof MulExpr || binOp instanceof SubExpr)) return;

		// generate add mutant
		if(!(binOp instanceof AddExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newAddExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (add)"));
		}

		// generate div mutant
		if(!(binOp instanceof DivExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newDivExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (div)"));
		} else { // TEO: swap op1 and op2
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newDivExpr(binOp.getOp2(), binOp.getOp1())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (div/swap)"));
		}

		// generate mul mutant
		if(!(binOp instanceof MulExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newMulExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (mul)"));
		}

		// generate sub mutant
		if(!(binOp instanceof SubExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newSubExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (sub)"));
		} else { // TEO: swap op1 and op2
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newSubExpr(binOp.getOp2(), binOp.getOp1())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (sub/swap)"));
		}

		// generate MOD (REM) mutant
		if((binOp.getOp1().getType() instanceof IntegerType || binOp.getOp1().getType() instanceof LongType)
				&& (binOp.getOp2().getType() instanceof IntegerType || binOp.getOp2().getType() instanceof LongType)) if(!(binOp instanceof RemExpr)) {
					NopStmt nop = Jimple.v().newNopStmt();
					int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
					mutations.add(mutationNumber);
					newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
					newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newRemExpr(binOp.getOp1(), binOp.getOp2())));
					newUnits.add(Jimple.v().newGotoStmt(after));
					newUnits.add(nop);
					nop.addTag(new StringTag("end of AOR (mod)"));
				} else { // teo: swap op1 and op2
					NopStmt nop = Jimple.v().newNopStmt();
					int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
					mutations.add(mutationNumber);
					newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
					newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newRemExpr(binOp.getOp2(), binOp.getOp1())));
					newUnits.add(Jimple.v().newGotoStmt(after));
					newUnits.add(nop);
					nop.addTag(new StringTag("end of AOR (mod/swap)"));
				}

		// generate letfOp mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), binOp.getOp1()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (leftOp)"));
		}

		// generate rightOp mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "AOR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), binOp.getOp2()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of AOR (rightOp)"));
		}

	}

	private void abs(SootClass sClass, SootMethod meth, AssignStmt assign, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {

		// check if on the right-hand side there is a numeric constant
		if(assign.getRightOp() instanceof NumericConstant) {
			NumericConstant rightOp = (NumericConstant) assign.getRightOp();

			// if it is not zero
			if(!((rightOp instanceof IntConstant && ((IntConstant) rightOp).value == 0) || (rightOp instanceof LongConstant && ((LongConstant) rightOp).value == 0)
					|| (rightOp instanceof FloatConstant && ((FloatConstant) rightOp).value == 0) || (rightOp instanceof DoubleConstant && ((DoubleConstant) rightOp).value == 0))) {
				// negate

				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ABS");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				AssignStmt mutatedAssign = (AssignStmt) assign.clone();
				mutatedAssign.setRightOp(rightOp.negate());
				newUnits.add(mutatedAssign);
				newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of ABS (neg)"));
			}

			// if it is a numeric constant, it's useless to create other ABS mutations!
			return;
		}


		Local tmp = tempLocals.get(assign.getLeftOp().getType());
		// tmp is not null: abs is applied only on primitive numbers!

		// if (MutantConfig.CUR_#className# != mutationNumberABS) goto @nop1
		// calculate tmp as #var# was calculatedbefore
		// #var# = Math.abs(tmp)
		// goto @after
		// @nop1
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ABS");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			AssignStmt cloned = (AssignStmt) assign.clone();
			cloned.setLeftOp(tmp);
			newUnits.add(cloned);
			if(assign.getLeftOp().getType() instanceof IntegerType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsInt.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof LongType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsLong.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof FloatType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsFloat.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof DoubleType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsDouble.makeRef(), tmp)));
			else System.err.println("ABS (ABS) errror: unknown type " + ((Local) assign.getLeftOp()).getType().getClass().getName());
			cloned = (AssignStmt) assign.clone();
			cloned.setRightOp(tmp);
			newUnits.add(cloned);

			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ABS (abs)"));
		}

		// if (MutantConfig.CUR_#className# != mutationNumberNABS) goto @nop2
		// #var# = -1 * Math.abs(#var#)
		// @nop2
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ABS");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			AssignStmt cloned = (AssignStmt) assign.clone();
			cloned.setLeftOp(tmp);
			newUnits.add(cloned);
			if(assign.getLeftOp().getType() instanceof IntegerType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsInt.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof LongType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsLong.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof FloatType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsFloat.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof DoubleType) newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newStaticInvokeExpr(mathAbsDouble.makeRef(), tmp)));
			else System.err.println("ABS (NABS) errror: unknown type " + ((Local) assign.getLeftOp()).getType().getClass().getName());
			newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newNegExpr(tmp)));
			cloned = (AssignStmt) assign.clone();
			cloned.setRightOp(tmp);
			newUnits.add(cloned);

			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ABS (nabs)"));
		}

		// if (MutantConfig.CUR_#className# != mutationNumberZPUSH) goto @nop3
		// UtilsABS.zpush(#var#)
		// @nop3
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ABS");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			AssignStmt cloned = (AssignStmt) assign.clone();
			cloned.setLeftOp(tmp);
			newUnits.add(cloned);
			if(assign.getLeftOp().getType() instanceof IntegerType) newUnits.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(absZpushInt.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof LongType) newUnits.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(absZpushLong.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof FloatType) newUnits.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(absZpushFloat.makeRef(), tmp)));
			else if(assign.getLeftOp().getType() instanceof DoubleType) newUnits.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(absZpushDouble.makeRef(), tmp)));
			else System.err.println("ABS (ZPUSH) errror: unknown type " + ((Local) assign.getLeftOp()).getType().getClass().getName());
			cloned = (AssignStmt) assign.clone();
			cloned.setRightOp(tmp);
			newUnits.add(cloned);

			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ABS (zpush)"));
		}
	}

	// logical connector replacement
	private void lcr(SootClass sClass, SootMethod meth, AssignStmt assign, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {
		if(!(assign.getRightOp() instanceof BinopExpr)) return;

		BinopExpr binOp = (BinopExpr) assign.getRightOp();

		if(!(binOp instanceof AndExpr || binOp instanceof OrExpr || binOp instanceof XorExpr)) return;

		// generate add mutant
		if(!(binOp instanceof AndExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newAndExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (and)"));
		}

		// generate or mutant
		if(!(binOp instanceof OrExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newOrExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (or)"));
		}

		// generate xor mutant
		if(!(binOp instanceof XorExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newXorExpr(binOp.getOp1(), binOp.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (xor)"));
		}

		// generate letfOp mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), binOp.getOp1()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (leftOp)"));
		}

		// generate rightOp mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), binOp.getOp2()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (rightOp)"));
		}

		// generate true mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), IntConstant.v(1)));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (true)"));
		}

		// generate false mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "LCR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), IntConstant.v(0)));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of LCR (false)"));
		}
	}

	// relational operator replacement
	private void ror(SootClass sClass, SootMethod meth, IfStmt ifStmt, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {
		if(!(ifStmt.getCondition() instanceof ConditionExpr)) return;

		ConditionExpr condExpr = (ConditionExpr) ifStmt.getCondition();

		Type type1 = condExpr.getOp1().getType();
		Type type2 = condExpr.getOp2().getType();
		boolean numbers = ((type1 instanceof IntegerType && !(type1 instanceof BooleanType)) || type1 instanceof LongType || type1 instanceof FloatType || type1 instanceof DoubleType)
		&& ((type2 instanceof IntegerType && !(type2 instanceof BooleanType)) || type2 instanceof LongType || type2 instanceof FloatType || type2 instanceof DoubleType);

		// generate eq (==) mutant
		if(!(condExpr instanceof EqExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newEqExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (eq)"));
		}

		// generate ge (>=) mutant
		if(numbers && !(condExpr instanceof GeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newGeExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (ge)"));
		}

		// generate gt (>) mutant
		if(numbers && !(condExpr instanceof GtExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newGtExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (gt)"));
		}

		// generate le (<=) mutant
		if(numbers && !(condExpr instanceof LeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newLeExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (le)"));
		}

		// generate lt (<) mutant
		if(numbers && !(condExpr instanceof LtExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newLtExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (lt)"));
		}

		// generate ne (!=) mutant
		if(!(condExpr instanceof NeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(condExpr.getOp1(), condExpr.getOp2()), ifStmt.getTarget()));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (ne)"));
		}

		// generate true mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newGotoStmt(ifStmt.getTarget()));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (true)"));
		}

		// generate false mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (false)"));
		}
	}

	// relational operator replacement
	private void ror(SootClass sClass, SootMethod meth, AssignStmt assign, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {
		if(!(assign.getRightOp() instanceof ConditionExpr)) return;

		ConditionExpr condExpr = (ConditionExpr) assign.getRightOp();
		Type type1 = condExpr.getOp1().getType();
		Type type2 = condExpr.getOp2().getType();
		boolean numbers = ((type1 instanceof IntegerType && !(type1 instanceof BooleanType)) || type1 instanceof LongType || type1 instanceof FloatType || type1 instanceof DoubleType)
		&& ((type2 instanceof IntegerType && !(type2 instanceof BooleanType)) || type2 instanceof LongType || type2 instanceof FloatType || type2 instanceof DoubleType);

		// generate eq (==) mutant
		if(!(condExpr instanceof EqExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newEqExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (eq)"));
		}

		// generate ge (>=) mutant
		if(numbers && !(condExpr instanceof GeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newGeExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (ge)"));
		}

		// generate gt (>) mutant
		if(numbers && !(condExpr instanceof GtExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newGtExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (gt)"));
		}

		// generate le (<=) mutant
		if(numbers && !(condExpr instanceof LeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newLeExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (le)"));
		}

		// generate lt (<) mutant
		if(numbers && !(condExpr instanceof LtExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newLtExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (lt)"));
		}

		// generate ne (!=) mutant
		if(!(condExpr instanceof NeExpr)) {
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), Jimple.v().newNeExpr(condExpr.getOp1(), condExpr.getOp2())));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (ne)"));
		}

		// generate true mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), IntConstant.v(1)));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (true)"));
		}

		// generate false mutant
		{
			NopStmt nop = Jimple.v().newNopStmt();
			int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "ROR");
			mutations.add(mutationNumber);
			newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
			newUnits.add(Jimple.v().newAssignStmt(assign.getLeftOp(), IntConstant.v(0)));
			newUnits.add(Jimple.v().newGotoStmt(after));
			newUnits.add(nop);
			nop.addTag(new StringTag("end of ROR (false)"));
		}
	}

	private void uoi(SootClass sClass, SootMethod meth, Unit oldUnit, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after) {
		// cloning, so it is possible to modify it directly!
		Unit unit = (Unit) oldUnit.clone();

		// extracts the value
		if(unit instanceof AssignStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((AssignStmt) unit).getRightOpBox(), ((AssignStmt) unit).getLeftOp().getType());
		else if(unit instanceof IfStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((IfStmt) unit).getConditionBox(), BooleanType.v());
		else if(unit instanceof InvokeStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((InvokeStmt) unit).getInvokeExprBox(), VoidType.v());
		else if(unit instanceof LookupSwitchStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((LookupSwitchStmt) unit).getKeyBox(), ((LookupSwitchStmt) unit).getKey().getType());
		else if(unit instanceof TableSwitchStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((TableSwitchStmt) unit).getKeyBox(), ((TableSwitchStmt) unit).getKey().getType());
		else if(unit instanceof ReturnStmt) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, null, ((ReturnStmt) unit).getOpBox(), meth.getReturnType());
		else return;
	}

	private void uoi(SootClass sClass, SootMethod meth, Unit unit, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after, ValueBox valueBox, Type valueType) {
		Value value = valueBox.getValue();

		if(value instanceof UnopExpr) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((UnopExpr) value).getOpBox(), valueType);
		else if(value instanceof CastExpr) uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((CastExpr) value).getOpBox(), ((CastExpr) value).getOp().getType());
		else if(value instanceof ConditionExpr) {
			uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((ConditionExpr) value).getOp1Box(), ((ConditionExpr) value).getOp1().getType());
			uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((ConditionExpr) value).getOp2Box(), ((ConditionExpr) value).getOp2().getType());
		} else if(value instanceof BinopExpr) {
			uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((BinopExpr) value).getOp1Box(), valueType);
			uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((BinopExpr) value).getOp2Box(), valueType);
		} else if(value instanceof InvokeExpr) {
			InvokeExpr invokeExpr = (InvokeExpr) value;
			SootMethod invokedMethod = invokeExpr.getMethod();
			for(int i = 0; i < invokeExpr.getArgCount(); i++)
				uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, invokeExpr.getArgBox(i), invokedMethod.getParameterType(i));
		} else if(value instanceof NumericConstant) {
			NumericConstant constant = (NumericConstant) value;

			for(NumericConstant mutation : getUoiValues(constant, valueType)) {
				valueBox.setValue(mutation);

				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}
		} else if(value instanceof Local)
			generateUOIMutants(sClass, meth, unit, mutations, newUnits, tmpLocal, after, valueBox);
		else if(value instanceof ConcreteRef) {

			if(value instanceof ArrayRef)
				uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, ((ArrayRef) value).getIndexBox(), ((ArrayRef) value).getIndex().getType());

			final Local tmp = tempLocals.get(valueType);
			if(tmp == null) return;

			NopStmt nop = Jimple.v().newNopStmt();
			newUnits.add(nop);
			nop.addTag(new StringTag("preparing ref access (UOI mutation)"));
			newUnits.add(Jimple.v().newAssignStmt(tmp, (Value) value.clone()));
			valueBox.setValue(tmp);

			generateUOIMutants(sClass, meth, unit, mutations, newUnits, tmpLocal, after, valueBox);

			valueBox.setValue(value);
		} else if(value instanceof NewArrayExpr) {

			NewArrayExpr newArrayExpr = (NewArrayExpr) value;
			uoi(sClass, meth, unit, mutations, newUnits, tmpLocal, after, newArrayExpr.getSizeBox(), newArrayExpr.getSize().getType());

		} else if(!(value instanceof StringConstant || value instanceof NewExpr || value instanceof NullConstant || value instanceof InstanceOfExpr)) {
			System.err.println("unexpected value: " + value + " (" + value.getClass().getName() + ") in unit: " + unit);
		}

		valueBox.setValue(value);
	}

	private void generateUOIMutants(SootClass sClass, SootMethod meth, Unit unit, Set<Integer> mutations, Chain<Unit> newUnits, Local tmpLocal, Unit after, ValueBox valueBox) {
		Value value = valueBox.getValue();
		Type valueType = value.getType();

		if(BooleanType.v().equals(valueType)) {
			Local tmp = tempLocals.get(BooleanType.v());
			valueBox.setValue(tmp);

			{ // generate true
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, IntConstant.v(1)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			{ // generate false
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, IntConstant.v(0)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			{ // generate inverse
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newSubExpr(IntConstant.v(1), value)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			return;
		}

		if(valueType instanceof IntegerType || valueType instanceof LongType || valueType instanceof FloatType || valueType instanceof DoubleType) {

			final Local tmp;
			final Value zero, one;
			if(valueType instanceof ByteType) {
				tmp = tempLocals.get(ByteType.v());
				zero = IntConstant.v(0);
				one = IntConstant.v(1);
			} else if(valueType instanceof IntegerType) {
				tmp = tempLocals.get(IntType.v());
				zero = IntConstant.v(0);
				one = IntConstant.v(1);
			} else if(valueType instanceof LongType) {
				tmp = tempLocals.get(LongType.v());
				zero = LongConstant.v(0);
				one = LongConstant.v(1);
			} else if(valueType instanceof FloatType) {
				tmp = tempLocals.get(FloatType.v());
				zero = FloatConstant.v(0);
				one = FloatConstant.v(1);
			} else { // doubleType
				tmp = tempLocals.get(DoubleType.v());
				zero = DoubleConstant.v(0);
				one = DoubleConstant.v(1);
			}

			valueBox.setValue(tmp);

			// generate inverse
			if(!(valueType instanceof ByteType)) {
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newNegExpr(value)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			{ // generate val+1
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newAddExpr(value, one)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			{ // generate val-1
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, Jimple.v().newSubExpr(value, one)));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			{ // generate 0
				NopStmt nop = Jimple.v().newNopStmt();
				int mutationNumber = ConfigHandler.singleton.getMutationNumber(sClass, meth, "UOI");
				mutations.add(mutationNumber);
				newUnits.add(Jimple.v().newIfStmt(Jimple.v().newNeExpr(tmpLocal, IntConstant.v(mutationNumber)), nop));
				newUnits.add(Jimple.v().newAssignStmt(tmp, zero));
				newUnits.add((Unit) unit.clone());
				if(after != null) newUnits.add(Jimple.v().newGotoStmt(after));
				newUnits.add(nop);
				nop.addTag(new StringTag("end of UOI"));
			}

			return;
		}
	}

	private static NumericConstant[] getUoiValues(NumericConstant constant, Type type) {
		if(constant instanceof IntConstant && BooleanType.v().equals(type)) {
			IntConstant intConst = (IntConstant) constant;
			return new NumericConstant[] { IntConstant.v(intConst.value > 0 ? 0 : 1), IntConstant.v(0), IntConstant.v(1) };
		}

		if(constant instanceof IntConstant) {
			IntConstant intConst = (IntConstant) constant;
			if(intConst.value == 0)
				return new NumericConstant[] { IntConstant.v(-1 * intConst.value), IntConstant.v(intConst.value + 1), IntConstant.v(intConst.value - 1)};
			else
				return new NumericConstant[] { IntConstant.v(-1 * intConst.value), IntConstant.v(intConst.value + 1), IntConstant.v(intConst.value - 1), IntConstant.v(0)};
		}

		if(constant instanceof LongConstant) {
			LongConstant longConst = (LongConstant) constant;
			if(longConst.value == 0)
				return new NumericConstant[] { LongConstant.v(-1 * longConst.value), LongConstant.v(longConst.value + 1), LongConstant.v(longConst.value - 1)};
			else
				return new NumericConstant[] { LongConstant.v(-1 * longConst.value), LongConstant.v(longConst.value + 1), LongConstant.v(longConst.value - 1), LongConstant.v(0) };
		}

		if(constant instanceof FloatConstant) {
			FloatConstant floatConst = (FloatConstant) constant;
			if(floatConst.value == 0)
				return new NumericConstant[] { FloatConstant.v(-1 * floatConst.value), FloatConstant.v(floatConst.value + 1), FloatConstant.v(floatConst.value - 1), FloatConstant.v(Float.MIN_NORMAL) };
			else
				return new NumericConstant[] { FloatConstant.v(-1 * floatConst.value), FloatConstant.v(floatConst.value + 1), FloatConstant.v(floatConst.value - 1), FloatConstant.v(0) };
		}

		if(constant instanceof DoubleConstant) {
			DoubleConstant doubleConst = (DoubleConstant) constant;
			if(doubleConst.value == 0)
				return new NumericConstant[] { DoubleConstant.v(-1 * doubleConst.value), DoubleConstant.v(doubleConst.value + 1), DoubleConstant.v(doubleConst.value - 1), DoubleConstant.v(Double.MIN_NORMAL) };
			else
				return new NumericConstant[] { DoubleConstant.v(-1 * doubleConst.value), DoubleConstant.v(doubleConst.value + 1), DoubleConstant.v(doubleConst.value - 1), DoubleConstant.v(0) };
		}

		System.err.println("unknown type of constant: " + constant + " (" + constant.getClass().getName() + ")");
		return new NumericConstant[0];
	}
}
